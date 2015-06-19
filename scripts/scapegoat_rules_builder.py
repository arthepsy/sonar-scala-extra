#!/usr/bin/env python
# -*- coding: utf-8 -*-
import sys, os, errno, regex
import click
from lxml import etree

class Config(object):
	def __init__(self, verbose=False):
		self.verbose = verbose
		self.scapegoat_dir = None
		self.scapegoat_rules = None

class ScapegoatRuleRepositoryBuilder():
	DEPRECATED_KEYS = ['EmptyCatchBlock',     # => SwallowedException
	                   'PreferVectorEmpty',   # removed, issue #73
	                   'NullUse',             # split in NullAssignment|NullParameter, issue #53
	                   'UnneccessryOverride'] # => NoOpOverride
	OVERRIDE_LEVELS = {'UnusedMethodParameter': 'Warning'}
	CODE_DESC_FIXES = {
		'rait ${cdef.name}': 'rait',                # fix for: LonelySealedTrait
		'${count(vparamss)}': 'too many',           # fix for: MaxParameters
		'${dd.symbol.fullName}': 'Symbol',          # fix for: RedundantFinalModifierOnMethod
		'ethod $name': 'ethod',                     # fix for: TypeShadowing
		'arameter ${tparam.name}': 'arameter',      # fix for: TypeShadowing
		'1 + x': 'x + 1',                           # fix fox: math.UseLog1P
		'${math}': 'math',                          # fix for: math.UseLog1P
		'ames (${name.decode}})': 'ames',           # fix for: style.AvoidOperatorOverload
		'(bad = $name)': '',                        # fix for: naming.ClassNames
		'arameter ($vparam)': 'arameter',           # fix for: unneccesary.UnusedmethodParameter
		'$unwritten': 'var',                        # fix for: unneccesary.VarCouldBeVal
	}
	
	def __init__(self, cfg):
		if cfg is None or not isinstance(cfg, Config):
			raise TypeError('config not valid', cfg)
		self.verbose = cfg.verbose
		self.scapegoat_dir = cfg.scapegoat_dir
		self.scapegoat_rules = cfg.scapegoat_rules
	
	def build(self):
		rules = {}
		self.parse_readme(rules)
		self.parse_code(rules)
		self.fix_rules(rules)
		
		xroot = etree.XML('<rules></rules>')
		xtree = etree.ElementTree(xroot)
		
		for key in rules:
			rule = rules[key]
			if not 'package' in rule:
				raise Exception('No package found for key: %s' % key) 
			if not 'level' in rule:
				raise Exception('No level found for key: %s' % key) 
			sq_key = '.'.join([rule['package'], key])
			sq_priority = self.get_sq_priority(rule['level'])
			if 'wikitext' in rule:
				sq_name = rule['wikitext']
			else:
				if self.verbose:
					print "warning: no wiki text for key: %s" % key
				sq_name = rule['code_text']
				if type(sq_name) is list:
					sq_name = self.get_single_name(key, sq_name, rule)
			if 'description' in rule:
				sq_desc = rule['description']
			else:
				sq_desc = ''
				if self.verbose:
					print "warning: no description for key: %s" % key
				if 'code_description' in rule:
					code_desc = rule['code_description']
					if type(code_desc) is list:
						code_desc = self.get_single_desc(key, code_desc, rule)
					if len(code_desc) > 0:
						if self.is_valid_desc(code_desc):
							sq_desc = code_desc
						else:
							if self.verbose:
								print 'warning: cannot use code description "%s" for key: %s' % (code_desc, key)
				if len(sq_desc) == 0:
					sq_desc = sq_name
			
			xrule = etree.SubElement(xroot, 'rule')
			xnode = etree.SubElement(xrule, 'key')
			xnode.text = sq_key
			xnode = etree.SubElement(xrule, 'name')
			xnode.text = etree.CDATA(sq_name)
			xnode = etree.SubElement(xrule, 'description')
			xnode.text = etree.CDATA(sq_desc)
			xnode = etree.SubElement(xrule, 'severity')
			xnode.text = sq_priority
			# others: status, tag?
		
		xtree.write(self.scapegoat_rules, pretty_print=True, encoding='UTF-8', xml_declaration=True)
	
	def parse_readme(self, rules):
		filepath = os.path.join(self.scapegoat_dir, 'README.md')
		if not os.path.isfile(filepath):
			raise Exception('Could not open "%s"' % filepath)
		f = open(filepath, 'r')
		content = f.read()
		f.close()
		
		m = regex.search(r'\n### Inspections(.*)\n### ', content, regex.DOTALL)
		if m is None:
			return
		blocks = m.group(1).split('#####')
		for block in blocks:
			block = block.strip()
			if '|Name|' in block:
				mx = regex.findall(r'\n\|([^\|]+)\|([^\|]*)\|', block)
				for m in mx:
					key = m[0].strip()
					wikitext = m[1].strip()
					if key and key[0] != '-' and key != 'Name':
						if key not in rules:
							rules[key] = {}
						rules[key]['wikitext'] = wikitext
			else:
				(key, desc) = block.split('\n', 1)
				key = ''.join(word[0].upper() + word[1:] for word in key.split())
				desc = desc.strip()
				if key not in rules:
					rules[key] = {}
				rules[key]['description'] = desc
		if not len(rules) > 0:
			raise Exception('invalid scapegoat readme')
	
	def parse_code(self, rules):
		for root, dirs, files in os.walk(self.scapegoat_dir):
			for file in files:
				filepath = os.path.join(root, file)
				(filebase, extension) = os.path.splitext(filepath)
				if extension != '.scala':
					continue
				if filebase.endswith('Stub'):
					continue
				f = open(filepath, 'r')
				content = f.read()
				f.close()
				m = regex.search(r'package\s+([^\s]+)', content)
				if m is None:
					raise Exception('Could not locate package in "%s"' % filepath)
				package = m.group(1).strip()
				
				m = regex.search(r"class\s+([^\s]+)\s+extends\s+Inspection\s+\{", content)
				if m is None:
					continue
				classname = m.group(1)
				mx = regex.findall(r'\.warn(?P<rec>\(((?>[^\(\)]+|(?&rec))*)\))', content)
				for m in mx:
					line = self.cleanline(m[1])
					args = line.split(',', 3)
					key = classname
					text = self.purify_code_text(args[0])
					level = self.get_level(key, args[2].strip())
					if ',' in args[3]:
						m2 = regex.match(r'^(.*),[^,]+$', args[3], regex.DOTALL)
						desc = m2.group(1)
					else:
						desc = args[3]
					desc = self.purify_code_desc(desc)
					
					if not key in rules:
						rules[key] = {}
						if self.verbose:
							print "warning: undescribed key: %s" % key
					
					self.update_rule(rules, key, 'package', package)
					self.update_rule(rules, key, 'level', level)
					self.update_rule(rules, key, 'code_text', text)
					self.update_rule(rules, key, 'code_description', desc)
	
	@staticmethod
	def cleanline(s):
		return ' '.join(s.split()).strip()
	
	@staticmethod
	def cleantext(s):
		s = s.strip()
		if s[0] == 's':
			s = s[1:]
		return s.lstrip('"').rstrip('"')
	
	def get_level(self, key, level):
		if key in self.OVERRIDE_LEVELS:
			return self.OVERRIDE_LEVELS[key]
		m = regex.search(r'Levels\.(.+)', level)
		if m:
			return m.group(1)
		else:
			raise Exception('Could not parse level "%s" for key: "%s"' % (level, key))
	
	def get_single_name(self, key, texts, rule):
		raise Exception("Multiple names found for key: %s\nrule:%s" % (key, rule))
	
	def get_single_desc(self, key, descs, rule):
		#raise Exception("Multiple descriptions found for key: %s\nrule: %s" % (key, rule))
		desc = '\n'.join(descs)
		return desc

	def is_valid_desc(self, desc):
		if '"' in desc:
			return False
		elif '$' in desc:
			return False
		else:
			return True
	
	def purify_code_text(self, text):
		text = self.cleantext(text)
		text = text.replace('${math}', 'math')
		return text
	
	def chop_code_desc(self, desc, pattern):
		rx = regex.compile('^(.*\s)?' + pattern + '(\s.*)?$', regex.DOTALL)
		m = rx.search(desc)
		matched = m is not None
		if matched:
			desc_head = (m.group(1) or '').rstrip()
			if desc_head.endswith(': " +'):
				desc_head = desc_head[:-5]
			elif desc_head.endswith(' " +'):
				desc_head = desc_head[:-4]
			desc_tail = (m.group(2) or '').lstrip()
			if desc_tail.startswith('+ ".'):
				desc_tail = desc_tail[4:]
			desc = (desc_head + desc_tail).strip()
		return [matched, desc]
	
	def purify_code_desc(self, desc):
		desc = self.cleantext(desc)
		desc = desc.replace('" + "', '\n')
		desc = regex.sub(r'\s\.', '.', desc)
		for k, v in self.CODE_DESC_FIXES.iteritems():
			desc = desc.replace(k, v)
		[chooped, desc] = self.chop_code_desc(desc, '[^\s]+\.take\([0-9]+\)')
		[chooped, desc] = self.chop_code_desc(desc, 'tree\.toString\(\)')
		[chooped, desc] = self.chop_code_desc(desc, 'tree\.pos\.line')
		if chooped:
			if desc.endswith(' on line'):
				desc = desc[:-8]
		return desc
	
	def fix_rules(self, rules):
		for key in self.DEPRECATED_KEYS:
			rules.pop(key, None)
	
	def update_rule(self, rules, key, prop, value):
		multiple = False
		if prop in rules[key]:
			if type(rules[key][prop]) is list:
				if value not in rules[key][prop]:
					multiple = True
					rules[key][prop].append(value) 
			else:
				if rules[key][prop] != value:
					multiple = True
					rules[key][prop] = [rules[key][prop], value]
		if multiple and self.verbose:
			print "warning: multiple %s on key: %s" % (prop, key)
		else:
			rules[key][prop] = value
	
	@staticmethod
	def get_sq_priority(level):
		if level == 'Info':
			return 'INFO'
		elif level == 'Warning':
			return 'MINOR'
		elif level == 'Error':
			return 'MAJOR'
		else:
			raise Exception('Could not get priority for level "%s"' % level)

class CmdLine():
	_type_dir = click.Path(exists=True, file_okay=False, dir_okay=True, readable=True, resolve_path=True)
	_type_rofile = click.Path(exists=True, file_okay=True, dir_okay=False, readable=True, resolve_path=True)
	_type_rwfile = click.Path(exists=False, file_okay=True, dir_okay=False, writable=True, resolve_path=True)
	
	def run(self):
		self.cli()
	
	@click.group(invoke_without_command=True, no_args_is_help=True, context_settings=dict(help_option_names=['-h', '--help']))
	@click.option('--verbose', '-v', default=False, is_flag=True)
	@click.argument('scapegoat_dir', metavar='<scapegoat_dir>', type=_type_dir)
	@click.argument('scapegoat_rules', metavar='<scapegoat_rules>', type=_type_rwfile)
	@click.pass_context
	def cli(ctx, verbose, scapegoat_dir, scapegoat_rules):
		cfg = ctx.ensure_object(Config)
		cfg.verbose = verbose
		cfg.scapegoat_dir = scapegoat_dir
		cfg.scapegoat_rules = scapegoat_rules
		builder = ScapegoatRuleRepositoryBuilder(cfg)
		builder.build()

if __name__ == '__main__':
	cmd = CmdLine()
	cmd.run()
