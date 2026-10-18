package org.mtr.mod.data;
/** Natural exit order, including 2 before 10 and alphanumeric MTR exit names. */
public final class SillonSignageNetworkOrdering {
    private SillonSignageNetworkOrdering(){}
    public static int compare(String a,String b){
        int i=0,j=0;while(i<a.length()&&j<b.length()){
            if(Character.isDigit(a.charAt(i))&&Character.isDigit(b.charAt(j))){
                int x=i,y=j;while(i<a.length()&&Character.isDigit(a.charAt(i)))i++;while(j<b.length()&&Character.isDigit(b.charAt(j)))j++;
                String u=a.substring(x,i).replaceFirst("^0+(?!$)",""),v=b.substring(y,j).replaceFirst("^0+(?!$)","");
                int c=Integer.compare(u.length(),v.length());if(c==0)c=u.compareTo(v);if(c!=0)return c;
            }else{int c=Character.compare(Character.toLowerCase(a.charAt(i++)),Character.toLowerCase(b.charAt(j++)));if(c!=0)return c;}
        }return Integer.compare(a.length()-i,b.length()-j);
    }
}
