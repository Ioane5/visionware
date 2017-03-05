package ge.ioane.visionware.model;

import org.greenrobot.greendao.annotation.Entity;
import org.greenrobot.greendao.annotation.Generated;

/**
 * Created by ioane5 on 3/4/17.
 */
@Entity
public class Item {

    private String name;
    private double Xposition;
    private double Yposition;
    private double Zposition;

    public Item(String name, double xposition, double yposition) {
        this.name = name;
        Xposition = xposition;
        Yposition = yposition;
        Zposition = 0; // not used
    }

    @Generated(hash = 2135078682)
    public Item(String name, double Xposition, double Yposition, double Zposition) {
        this.name = name;
        this.Xposition = Xposition;
        this.Yposition = Yposition;
        this.Zposition = Zposition;
    }

    @Generated(hash = 1470900980)
    public Item() {
    }

    public String getName() {
        return name;
    }

    public double getXposition() {
        return Xposition;
    }

    public double getYposition() {
        return Yposition;
    }

    public double getZposition() {
        return Zposition;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setXposition(double Xposition) {
        this.Xposition = Xposition;
    }

    public void setYposition(double Yposition) {
        this.Yposition = Yposition;
    }

    public void setZposition(double Zposition) {
        this.Zposition = Zposition;
    }

    public double[] getPosition() {
        return new double[]{Xposition, Yposition, Zposition};
    }

    @Override
    public String toString() {
        return "Item{" +
                "name='" + name + '\'' +
                ", X=" + Xposition +
                ", Y=" + Yposition +
                ", Z=" + Zposition +
                '}';
    }
}
