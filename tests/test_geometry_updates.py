"""Run the production geometry/change policy on the JVM; physical layout remains separate."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
HARNESS = '''package com.cbkii.ts18launcher;
public class GeometryHarness {
 static Ts18Geometry.Inputs input(int w,int h,int t,int r,boolean rail,boolean radio) {
  return new Ts18Geometry.Inputs(w,h,0,t,r,0,rail,radio);
 }
 static void check(boolean value) { if (!value) throw new AssertionError(); }
 public static void main(String[] args) {
  Ts18Geometry.ChangeTracker changes = new Ts18Geometry.ChangeTracker();
  Ts18Geometry.Inputs base = input(1280,720,55,55,false,false);
  check(changes.shouldApply(base));
  for(int i=0;i<10000;i++) check(!changes.shouldApply(input(1280,720,55,55,false,false)));
  switch(args[0]) {
   case "preferences":
    check(changes.shouldApply(input(1280,720,55,55,true,false)));
    check(changes.shouldApply(input(1280,720,55,55,true,true)));
    check(changes.shouldApply(base)); break;
   case "insets":
    check(changes.shouldApply(input(1280,720,70,55,false,false)));
    check(changes.shouldApply(input(1280,720,70,65,false,false)));
    Ts18Geometry.Layout g=Ts18Geometry.resolve(input(1280,720,70,65,false,false));
    check(g.top==70 && g.safeRight==1215); break;
   case "return":
    check(changes.shouldApply(input(1227,665,0,0,false,false)));
    check(changes.shouldApply(base)); check(!changes.shouldApply(base)); break;
   case "orientation":
    check(changes.shouldApply(input(720,1280,0,0,false,false)));
    check(changes.shouldApply(base)); break;
   case "child":
    changes.invalidate(); check(changes.shouldApply(base)); check(!changes.shouldApply(base)); break;
   case "unmeasured":
    check(!changes.shouldApply(input(0,0,0,0,false,false)));
    check(!changes.shouldApply(base)); break;
   case "decor":
    check(Ts18Geometry.residualStartInset(55,55)==0);
    check(Ts18Geometry.residualEndInset(55,0,1225,1280)==0);
    check(Ts18Geometry.residualStartInset(55,0)==55);
    check(Ts18Geometry.residualEndInset(55,0,1280,1280)==55);
    Ts18Geometry.Layout full=Ts18Geometry.resolve(base);
    check(full.top==55 && full.safeRight==1225);
    Ts18Geometry.Layout fitted=Ts18Geometry.resolve(input(1227,665,0,0,false,false));
    check(fitted.top==0 && fitted.safeRight==1227); break;
   default: throw new AssertionError(args[0]);
  }
 }
}'''

class GeometryUpdateTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if shutil.which('java') is None:
            raise unittest.SkipTest('Java unavailable: geometry policy NOT_RUN')
        modules = subprocess.run(['java', '--list-modules'], capture_output=True, text=True, timeout=5)
        if modules.returncode != 0 or 'jdk.compiler@' not in modules.stdout:
            raise unittest.SkipTest('Java compiler module unavailable: geometry policy NOT_RUN')
        cls.temp = tempfile.TemporaryDirectory()
        cls.directory = Path(cls.temp.name)
        harness = cls.directory / 'GeometryHarness.java'
        harness.write_text(HARNESS)
        source = ROOT / 'launcher/src/main/java/com/cbkii/ts18launcher/Ts18Geometry.java'
        subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-d',str(cls.directory),
                        str(source),str(harness)],check=True,capture_output=True)

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def test_repeated_callbacks_and_geometry_boundaries(self):
        for scenario in ('preferences','insets','return','orientation','child','unmeasured','decor'):
            with self.subTest(scenario=scenario):
                subprocess.run(['java','-cp',str(self.directory),'com.cbkii.ts18launcher.GeometryHarness',scenario],
                               check=True,capture_output=True,timeout=5)
