from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/'launcher/src/main/java/com/cbkii/ts18launcher'
HELPER=ROOT/'launcher/src/main/assets/nav/nav-window.sh'
class NavigationSurfaceExperimentContractTest(unittest.TestCase):
 def test_single_surface_policy_has_safe_default_and_legacy_leaflet_migration(self):
  s=(JAVA/'HomeNavigationSurfacePolicy.java').read_text();[self.assertIn(v,s) for v in ('fullscreen','leaflet','raw_freeform','android_pip')];self.assertIn('return FULLSCREEN',s);self.assertIn('LauncherPrefs.KEY_MAP_ENABLED',s)
 def test_freeform_and_pip_are_separate_backends(self):
  a=(JAVA/'RawFreeformTaskBackend.java').read_text();b=(JAVA/'AndroidPipBackend.java').read_text();self.assertIn('return "freeform"',a);self.assertIn('return "pip"',b);self.assertNotIn('TopwayFreeformBackend',a+b)
 def test_controller_repairs_known_task_before_relaunch(self):
  s=(JAVA/'NavigationWindowController.java').read_text();self.assertIn('verifyOrRepair',s);self.assertIn('backend.present(pkg,target,taskId',s);self.assertIn('"TASK_AMBIGUOUS"',s);self.assertNotIn('backend.focus(activity.getPackageName()',s)
 def test_pip_never_enables_global_force_resizable_or_steals_foreign_pip(self):
  s=HELPER.read_text();self.assertIn('PIP_OCCUPIED_BY_OTHER_APP',s);self.assertIn('PIP_UNSUPPORTED',s);self.assertIn('move-top-activity-to-pinned-stack',s);self.assertNotIn('settings put global force_resizable_activities',s);self.assertNotIn('setprop',s)
 def test_helper_fails_ambiguous_package_acquisition_closed(self):
  s=HELPER.read_text();self.assertIn('TASK_AMBIGUOUS',s);self.assertIn('count>1&&hint=="0"',s)
 def test_success_checks_real_mode_display_bounds_component(self):
  s=(JAVA/'NavigationWindowController.java').read_text();self.assertIn('result.displayId!=0',s);self.assertIn('result.component.startsWith(pkg+"/")',s);self.assertIn('target.toString().equals(result.bounds)',s);self.assertIn('result.windowingMode==expectedMode',s)
 def test_testing_build_has_exact_source_provenance(self):
  b=(ROOT/'launcher/build.gradle.kts').read_text();w=(ROOT/'.github/workflows/validate.yml').read_text();p=(JAVA/'NativeNavigationPanel.java').read_text();self.assertIn('SOURCE_REVISION',b);self.assertIn('build_source_revision',b);self.assertIn('github.event.pull_request.head.sha',w);self.assertIn('BuildIdentity.summary',p)
 def test_video_binder_is_not_navigation_transport(self):
  self.assertNotIn('FLOATING_WINDOW_SERVER',(JAVA/'NavigationWindowController.java').read_text()+HELPER.read_text())
if __name__=='__main__':unittest.main()
