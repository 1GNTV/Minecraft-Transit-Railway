#!/usr/bin/env python3
"""GLACIER b116: compile and verify exception-free display parsing and production wiring."""
from pathlib import Path
from tempfile import TemporaryDirectory
import subprocess

ROOT=Path(__file__).resolve().parents[1]
PARSER=ROOT/"fabric/src/main/java/org/mtr/mod/client/DisplayNumberParsing.java"
IDRAWING=ROOT/"fabric/src/main/java/org/mtr/mod/client/IDrawing.java"
MODEL=ROOT/"fabric/src/main/java/org/mtr/mod/resource/ModelPropertiesPart.java"
RENDER=ROOT/"fabric/src/main/java/org/mtr/mod/render/RenderRails.java"
BATCHER=ROOT/"fabric/src/main/java/org/mtr/mod/render/RailSectorBatcher.java"
JAVA=r"""
import org.mtr.mod.client.DisplayNumberParsing;
import java.util.Random;

public class NoExceptionDisplayTest {
    static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
    static Integer oldParser(String s) {
        try{return Integer.parseInt(s);}catch(NumberFormatException e){return null;}
    }
    static void compare(String s) {
        Integer expected=oldParser(s);
        Integer actual=DisplayNumberParsing.parseIntOrNull(s);
        check(java.util.Objects.equals(expected,actual),
             "numeric semantics mismatch input="+s+" expected="+expected+" actual="+actual);
    }
    public static void main(String[] args) {
        for(String text:new String[]{
            "", "0","1","0001","-0","+0","-2147483648","2147483647",
            "2147483648","-2147483649","--1","+-3","+","-","12 3"," 12",
            "12 ","NaN","N/A","Paris","Ligne 14","0x10","١٢","１２","0000000000001",
            "9999999999999999999999999999999","-00000000000000000000000001"})compare(text);
        Random r=new Random(0x4A7B);
        String alphabet="0123456789ABCDEF+- ▁◇\u0661\u0662\uFF11\uFF12";
        for(int i=0;i<40_000;i++) {
            int len=r.nextInt(25);
            StringBuilder s=new StringBuilder();
            for(int j=0;j<len;j++)s.append(alphabet.charAt(r.nextInt(alphabet.length())));
            compare(s.toString());
        }
        check(DisplayNumberParsing.parseIntOrNull(null)==null,"null display safely ignored");
        System.out.println("PASS: 40k malformed numeric displays, Unicode digits, signs and overflow without exceptions");
    }
}
"""
def main():
    source=MODEL.read_text(encoding="utf-8")
    format_text=source.split("private String formatText(Vehicle vehicle)",1)[1].split("private HorizontalAlignment",1)[0]
    assert "parsedDisplayOptions" in format_text and "EnumHelper.valueOf" not in format_text, "formatText must not resolve enums per frame"
    assert source.count("parsedDisplayOptions = resolveDisplayOptions()")==3, "every constructor compiles options"
    assert "sevenSegmentNumber.intValue()" in source,"seven segment renderer must reuse pre-parsed number"
    assert "Integer.parseInt(numberString)" not in IDRAWING.read_text(encoding="utf-8"), "old exception parsing still present"
    assert "DisplayNumberParsing.parseIntOrNull(numberString)" in IDRAWING.read_text(encoding="utf-8")
    rails=RENDER.read_text(encoding="utf-8")
    batch=BATCHER.read_text(encoding="utf-8")
    assert "RailSectorBatcher.submitVisible" in rails,"retained rail renderer not wired to render source"
    assert "RailSectorBatcher.submit(railResource" in rails, "fallback path must stay active"
    assert "RailLightFrameCache.get(clientWorld,piece.lightPosition())" in batch, "dynamic lighting must not be cached"
    assert "RAIL_PLANS.clear()" in batch and "planWorld!=clientWorld.data" in batch, "plan invalidation missing"
    with TemporaryDirectory(prefix="glacier-display-") as folder:
        p=Path(folder)/"NoExceptionDisplayTest.java"
        p.write_text(JAVA,encoding="utf-8")
        subprocess.run(["javac","-d",folder,str(PARSER),str(p)],check=True)
        subprocess.run(["java","-cp",folder,"NoExceptionDisplayTest"],check=True)

if __name__=="__main__":main()
