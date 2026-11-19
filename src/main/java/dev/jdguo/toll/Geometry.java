package dev.jdguo.toll;

/** Continuous segment/cylinder intersection, including high-speed outside-to-outside passes. */
public final class Geometry {
    private Geometry() {}
    public record Position(double x, double y, double z) {}
    public static boolean inside(Position p, Position c, double radius, double height) {
        return Math.abs(p.y-c.y)<=height && Math.hypot(p.x-c.x,p.z-c.z)<=radius;
    }
    public static boolean crosses(Position a, Position b, Position c, double r, double h) {
        double dx=b.x-a.x, dy=b.y-a.y, dz=b.z-a.z;
        double lo=0,hi=1;
        if(Math.abs(dy)<1e-12) { if(Math.abs(a.y-c.y)>h) return false; }
        else {double t1=(c.y-h-a.y)/dy,t2=(c.y+h-a.y)/dy;lo=Math.max(lo,Math.min(t1,t2));hi=Math.min(hi,Math.max(t1,t2));}
        double x=a.x-c.x,z=a.z-c.z,A=dx*dx+dz*dz,B=2*(x*dx+z*dz),C=x*x+z*z-r*r;
        if(A<1e-12) return C<=0 && lo<=hi;
        double d=B*B-4*A*C;if(d<0)return false;
        double root=Math.sqrt(d);lo=Math.max(lo,(-B-root)/(2*A));hi=Math.min(hi,(-B+root)/(2*A));
        return lo<=hi;
    }
    public static boolean insideBox(Position p, Position c, double hx, double h, double hz) {
        return Math.abs(p.x-c.x)<=hx && Math.abs(p.y-c.y)<=h && Math.abs(p.z-c.z)<=hz;
    }

    /** Clip the same movement interval against each axis; both endpoints may be outside. */
    public static boolean crossesBox(Position a, Position b, Position c, double hx, double h, double hz) {
        double[] range={0,1};
        return clip(a.x-c.x,b.x-a.x,hx,range) && clip(a.y-c.y,b.y-a.y,h,range)
                && clip(a.z-c.z,b.z-a.z,hz,range);
    }
    private static boolean clip(double a,double delta,double extent,double[] range) {
        if(delta==0)return Math.abs(a)<=extent;
        double t1=(-extent-a)/delta,t2=(extent-a)/delta;
        range[0]=Math.max(range[0],Math.min(t1,t2));
        range[1]=Math.min(range[1],Math.max(t1,t2));
        return range[0]<=range[1];
    }

    public static boolean overlaps(TollStore.Point a,TollStore.Point b,double pad) {
        if(!a.dimension.equals(b.dimension) || Math.abs(a.center().y-b.center().y)>a.halfHeight()+b.halfHeight()+2*pad)return false;
        double dx=Math.abs(a.x-b.x),dz=Math.abs(a.z-b.z);
        if(!a.isBox()&&!b.isBox())return Math.hypot(dx,dz)<=a.radius+b.radius+2*pad;
        if(a.isBox()&&b.isBox())return dx<=a.radius+b.radius+2*pad && dz<=a.halfZ+b.halfZ+2*pad;
        var box=a.isBox()?a:b;var circle=a.isBox()?b:a;
        return Math.hypot(Math.max(0,dx-box.radius-pad),Math.max(0,dz-box.halfZ-pad))<=circle.radius+pad;
    }
}
