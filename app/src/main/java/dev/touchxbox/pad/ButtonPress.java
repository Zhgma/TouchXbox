package dev.touchxbox.pad;

/** A pointer owns its press until it ends; optional retention only affects moves. */
public final class ButtonPress {
    private boolean active,retain,down;
    public void begin(boolean holdOutside){active=down=true;retain=holdOutside;}
    public boolean move(boolean inside){down=active&&(inside||retain);return down;}
    public void end(){active=down=false;}
    public boolean down(){return down;}
}
