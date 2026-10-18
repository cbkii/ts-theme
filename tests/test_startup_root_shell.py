"""Production root verbs use the existing exact-device task parser and command boundary doubles."""
import unittest
from unittest.mock import patch
import test_navigation_lifecycle as nav


class StartupRootShellTests(unittest.TestCase):
    def run_root(self, action, package='com.navimods.radio', **changes):
        source = nav.ANDROID.replace("        print('mCurrentFocus=Window{fake u0 ' + focus_component + '}')\n", "").replace("if a[0] == 'window':", '''if a[0] == 'window':
        focused = s.get('startup_focus', (s.get('nav_component','com.navimods.radio/.RadioActivity') if s['focus']==42 else 'com.cbkii.ts18launcher/.HomeAlias'))
        print('  mCurrentFocus=' + ('null' if s.get('blank_startup_focus') else 'Window{abc u0 '+focused+'}'))''')
        source = source.replace("print('      * Hist #0: ActivityRecord{fake u0 %s t%s}' % (component, task))",
            "print('      * Hist #0: ActivityRecord{fake u0 %s t%s}' % (component, task))\n        if task == 7 and s.get('extra_home_activity'): print('      * Hist #1: ActivityRecord{fake u0 com.android.permissioncontroller/.Grant t7}')")
        source = source.replace("s['mode'] = int(a[a.index('--windowingMode')+1]);", "s['mode'] = int(a[a.index('--windowingMode')+1]) if '--windowingMode' in a else 1;")
        options = dict(nav_package=package, nav_component=package+'/.RadioActivity',
                       mode=1, focus=7 if action=='startup-source' else 42)
        options.update(changes)
        args=[action,'0',package,package+'/.RadioActivity']
        if action=='startup-home': args+=['42']
        args+=['com.cbkii.ts18launcher','7']
        with patch.object(nav, 'ANDROID', source):
            return nav.NavigationLifecycleTest().run_helper(args, **options)

    def test_start_dispatches_once_from_exact_home(self):
        r,s=self.run_root('startup-source')
        self.assertEqual(r.returncode,0,r.stdout+r.stderr)
        self.assertIn('STARTUP_SOURCE_DISPATCHED',r.stdout)
        self.assertEqual(s['start_dispatches'],1)
        self.assertEqual([c for c in s['commands'] if c[0]!='start'],[])

    def test_home_focus_preserves_source_task_and_never_redelivers(self):
        r,s=self.run_root('startup-home')
        self.assertEqual(r.returncode,0,r.stdout+r.stderr)
        self.assertIn('STARTUP_HOME_RESTORED',r.stdout)
        self.assertEqual(s['commands'],[['task','focus','7']])
        self.assertEqual(s['start_dispatches'],0)
        self.assertEqual(s['mode'],1)

    def test_permission_settings_focus_and_task_changes_do_not_mutate(self):
        cases=[dict(focus=99),dict(blank_startup_focus=True),dict(startup_focus='com.android.permissioncontroller/.Grant'),
               dict(nav_component='com.navimods.radio/.SettingsActivity'),dict(mode=5),dict(nav_alive=False),dict(extra_home_activity=True)]
        for change in cases:
            with self.subTest(change=change):
                r,s=self.run_root('startup-home',**change)
                self.assertNotEqual(r.returncode,0)
                self.assertEqual(s['commands'],[])

    def test_source_does_not_launch_over_other_app_or_home_settings(self):
        for change in (dict(focus=99),dict(blank_startup_focus=True),dict(home_component='com.cbkii.ts18launcher/.SettingsActivity')):
            with self.subTest(change=change):
                r,s=self.run_root('startup-source',**change)
                self.assertNotEqual(r.returncode,0)
                self.assertEqual(s['start_dispatches'],0)

    def test_definite_rejection_and_unknown_exception_differ(self):
        r,s=self.run_root('startup-source',reject_launch=True)
        self.assertIn('STARTUP_SOURCE_REJECTED',r.stdout)
        self.assertEqual(s['start_dispatches'],1)
        r,s=self.run_root('startup-source',exception_launch=True)
        self.assertIn('STARTUP_SOURCE_UNCERTAIN',r.stdout)
        self.assertEqual(s['start_dispatches'],1)

    def test_protected_stock_radio_never_gets_a_root_activity_start(self):
        r,s=self.run_root('startup-source',package='com.tw.radio')
        self.assertIn('STARTUP_PACKAGE_BLOCKED',r.stdout)
        self.assertEqual(s['start_dispatches'],0)
