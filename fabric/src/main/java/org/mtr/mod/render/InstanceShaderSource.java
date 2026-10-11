package org.mtr.mod.render;
import java.util.*;
import java.util.regex.*;

/** Token-aware transformation of Oculus' already transformed two-stage GLSL. */
public final class InstanceShaderSource {
    public static final int CAPACITY=8;
    public record Pair(String vertex,String fragment) {}
    private static final Map<String,String> MATRICES=Map.of("iris_ModelViewMat","mtr_ModelViews","iris_ModelViewMatInverse","mtr_ModelViewInverses","iris_NormalMat","mtr_NormalMatrices");
    private static final Pattern TOKEN=Pattern.compile("[A-Za-z_][A-Za-z_0-9]*");
    private static final Pattern MAIN=Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(?:void)?\\s*\\)\\s*\\{");
    private InstanceShaderSource() {}
    public static Pair patch(String vertex,String fragment){
        if(vertex==null || fragment==null)return null;
        String v=mask(vertex),f=mask(fragment);
        if(v.contains("mtr_Instanced") || f.contains("mtr_Instanced") || !v.contains("uniform mat4 iris_ModelViewMat") || v.contains("gl_ModelViewMatrix") || f.contains("gl_ModelViewMatrix"))return null;
        if(!MAIN.matcher(v).find() || !MAIN.matcher(f).find())return null;
        // Legacy GLSL and macros with model matrices need a different transformation.
        for(String source:List.of(v,f)){
            Matcher version=Pattern.compile("(?m)^\\s*#version\\s+(\\d+)").matcher(source);
            if(!version.find() || Integer.parseInt(version.group(1))<150)return null;
            for(String line:source.split("\\R"))if(line.stripLeading().startsWith("#") && MATRICES.keySet().stream().anyMatch(line::contains))return null;
        }
        String pv=rewrite(vertex,true),pf=rewrite(fragment,false);
        return pv==null || pf==null?null:new Pair(pv,pf);
    }
    private static String rewrite(String source,boolean vertex){
        String masked=mask(source);Matcher main=MAIN.matcher(masked);if(!main.find())return null;

        List<int[]> declarations=new ArrayList<>();
        Matcher uniform=Pattern.compile("\\buniform\\b[^;{}]*;").matcher(masked);
        while(uniform.find())declarations.add(new int[]{uniform.start(),uniform.end()});
        StringBuilder result=new StringBuilder(source.length()+1000);Matcher tokens=TOKEN.matcher(masked);int cursor=0;
        while(tokens.find()){
            String target=MATRICES.get(tokens.group());if(target==null)continue;
            boolean declaration=false;for(int[] range:declarations)if(tokens.start()>=range[0] && tokens.end()<=range[1]){declaration=true;break;}
            if(declaration)continue;
            result.append(source,cursor,tokens.start());
            result.append("(mtr_Instanced ? ").append(target).append('[').append(vertex?"gl_InstanceID":"mtr_InstanceId").append("] : ").append(tokens.group()).append(')');
            cursor=tokens.end();
        }
        result.append(source,cursor,source.length());
        // Locate main again after replacements, while preserving comments/preprocessor text.
        String rewritten=result.toString();main=MAIN.matcher(mask(rewritten));if(!main.find())return null;
        String declarationsText="\nuniform bool mtr_Instanced;\nuniform mat4 mtr_ModelViews[8];\nuniform mat4 mtr_ModelViewInverses[8];\nuniform mat3 mtr_NormalMatrices[8];\nflat "+(vertex?"out":"in")+" int mtr_InstanceId;\n";
        String body=vertex?"\n mtr_InstanceId = mtr_Instanced ? gl_InstanceID : 0;\n":"";
        int declarationsAt=firstCode(mask(rewritten));
        if(declarationsAt<0)return null;
        return rewritten.substring(0,declarationsAt)+declarationsText+rewritten.substring(declarationsAt,main.end())+body+rewritten.substring(main.end());
    }
    private static int firstCode(String masked) {
        int start=0;boolean continuation=false;
        while(start<masked.length()) {
            int end=masked.indexOf('\n',start);if(end<0)end=masked.length();
            String line=masked.substring(start,end);String clean=line.stripLeading();
            boolean directive=continuation || clean.startsWith("#");
            continuation=directive && line.stripTrailing().endsWith("\\");
            if(!directive && !clean.isEmpty())return start+line.length()-clean.length();
            start=end+1;
        }return -1;
    }
    private static String mask(String source){
        char[] chars=source.toCharArray();boolean block=false,line=false;
        for(int i=0;i<chars.length;i++){
            char c=source.charAt(i),next=i+1<chars.length?source.charAt(i+1):0;
            if(line){if(c=='\n')line=false;else chars[i]=' ';continue;}
            if(block){if(c=='*' && next=='/'){chars[i]=chars[i+1]=' ';i++;block=false;}else if(c!='\n' && c!='\r')chars[i]=' ';continue;}
            if(c=='/' && (next=='/' || next=='*')){line=next=='/';block=next=='*';chars[i]=chars[i+1]=' ';i++;}
        }return new String(chars);
    }
}
