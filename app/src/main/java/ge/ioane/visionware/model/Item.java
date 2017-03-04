package ge.ioane.visionware.model;

/**
 * Created by ioane5 on 3/4/17.
 */
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
}
