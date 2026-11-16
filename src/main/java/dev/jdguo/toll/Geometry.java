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
}
