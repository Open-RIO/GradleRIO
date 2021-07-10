package my.vendor;

import edu.wpi.first.wpilibj.RobotBase;

public class MyVendorLibrary {

  public String whoAreYou() {
    return "The Developer";
  }

  public String whoAmI() {
    if (RobotBase.isReal())
      return "The Robot";
    else 
      return "A Simulation";
  }

}