package dev.touchxbox.pad;

/** SI units -> NS Pro controller axes and native sensor units. Pure math, no fake sensors. */
public final class MotionCodec {
    public static final double G=9.80665, ACCEL_PER_G=4096, GYRO_PER_DPS=14.247;
    public static short[] convert(float[] accel,float[] gyro,int rotation){
        short[] values=new short[6];convertVector(accel,rotation,ACCEL_PER_G/G,values,0);convertVector(gyro,rotation,GYRO_PER_DPS*180/Math.PI,values,3);return values;
    }
    private static void convertVector(float[] v,int rotation,double scale,short[] out,int offset){
        float sx=v[0],sy=v[1];
        switch(rotation){case 1:sx=-v[1];sy=v[0];break;case 2:sx=-v[0];sy=-v[1];break;case 3:sx=v[1];sy=-v[0];break;default:break;}
        // Controller X toward triggers (screen top), Y left, Z out of the screen.
        out[offset]=quantize(sy*scale);out[offset+1]=quantize(-sx*scale);out[offset+2]=quantize(v[2]*scale);
    }
    private static short quantize(double v){return (short)(Double.isNaN(v)||Double.isInfinite(v)?0:Math.max(-32768,Math.min(32767,Math.round(v))));}
}
