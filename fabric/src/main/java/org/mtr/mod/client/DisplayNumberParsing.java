package org.mtr.mod.client;

/**
 * Integer.parseInt compatibility without the NumberFormatException allocation
 * on arbitrary alphanumeric route/display text. A null result means the
 * seven-segment glyph is unsupported, matching the old try/catch no-op.
 */
public final class DisplayNumberParsing {
    private DisplayNumberParsing(){}

    public static Integer parseIntOrNull(String text) {
        if(text==null || text.isEmpty())return null;
        int index=0;
        boolean negative=false;
        int length=text.length();
        char first=text.charAt(0);
        if(first=='-' || first=='+'){
            negative=first=='-';
            index=1;
            if(index==length)return null;
        }
        int limit=negative?Integer.MIN_VALUE:-Integer.MAX_VALUE;
        int multmin=limit/10;
        int result=0;
        while(index<length) {
            int digit=Character.digit(text.charAt(index++),10);
            if(digit<0 || result<multmin)return null;
            result*=10;
            if(result<limit+digit)return null;
            result-=digit;
        }
        return negative?result:-result;
    }
}
