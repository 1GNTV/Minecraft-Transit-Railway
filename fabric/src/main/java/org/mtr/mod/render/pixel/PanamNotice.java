package org.mtr.mod.render.pixel;

import java.util.Objects;

/** An announcement keeps its own line badge, independently of the platform's line. */
public final class PanamNotice {
    public final String line,message;
    public final int color,priority;
    public PanamNotice(String line,int color,String message,int priority){
        this.line=line==null?"":line;this.color=color&0xFFFFFF;
        this.message=message==null?"":message;this.priority=priority>=3?3:priority>=1?1:0;
    }
    public PanamNotice withPriority(int value){return new PanamNotice(line,color,message,value);}
    @Override public boolean equals(Object other){
        if(!(other instanceof PanamNotice))return false;PanamNotice n=(PanamNotice)other;
        return color==n.color&&priority==n.priority&&line.equals(n.line)&&message.equals(n.message);
    }
    @Override public int hashCode(){return Objects.hash(line,color,message,priority);}
}
