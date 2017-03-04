package ge.ioane.visionware;

/**
 * Created by ioane5 on 3/4/17.
 */
public class Item {

    String name;
    double Xposition;
    double Yposition;

    public Item(String name, double xposition, double yposition) {
        this.name = name;
        Xposition = xposition;
        Yposition = yposition;
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
}
