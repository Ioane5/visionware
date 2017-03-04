package ge.ioane.visionware;

import org.rajawali3d.math.Quaternion;
import org.rajawali3d.math.vector.Vector2;
import org.rajawali3d.math.vector.Vector3;

/**
 * Created by ioane5 on 3/4/17.
 */
public abstract class RelativeCaltulator {
    private static final String TAG = RelativeCaltulator.class.getSimpleName();

    public static final double GRADUS_IN_RAD = 57.2958;
//    public static String lookAt(double[] rotation, double[] first, double[] second) {
//        Vector3 v1 = new Vector3(first);
//        Vector3 v2 = new Vector3(second);
//        v1.normalize();
//        v2.normalize();
//
//        float rotationAngle = (float)Math.acos(v1.dot(v2));
//
////        Vector3 rotationAxis = v1.clone().cross(v2);
////        rotationAxis.normalize();
//        return "" + rotationAngle;
//    }

    public static String lookAt(double[] rotation, double[] first, double[] second) {
        Vector2 point = new Vector2(second[0], second[1]);
        Vector2 cameraPos = new Vector2(first[0], first[1]);

        Quaternion q = new Quaternion(rotation[3], rotation[0], rotation[1], rotation[2]);
        // ROLL 3 : -3 PI, -PI
        double cameraAngle = -q.getRoll() * GRADUS_IN_RAD;

        Vector2 desiredForward = new Vector2(point.getX() - cameraPos.getX(), point.getY() - cameraPos.getY());
        double desiredHeading = Math.atan2(desiredForward.getY(), desiredForward.getX()) * GRADUS_IN_RAD;

        double rotation_needed = desiredHeading - cameraAngle;
//        if (rotation_needed > Math.PI)
//            rotation_needed -= 2 * Math.PI;
//        if (rotation_needed < -Math.PI)
//            rotation_needed += 2 * Math.PI;

        return "x " + first[0] + " y " + first[1] + "\n Angle " + cameraAngle + "\nDesired Heading" + desiredHeading + "\nrotation " + rotation_needed;
    }

    public static double distance(double[] first, double[] second) {
        Vector3 v1 = new Vector3(first);
        Vector3 v2 = new Vector3(second);

        v1.distanceTo(v2);
        return v1.distanceTo(v2);
    }
}
