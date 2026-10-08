package dev.touchxbox.pad;
/** Only included in the launcher-free QA APK. Never listens on the production port. */
public final class UpdateBridgeRunner {
    public static void main(String[] args)throws Exception{BridgeDaemon.run(args,37685);}
}
