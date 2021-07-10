import org.junit.Test;
import static org.junit.Assert.assertEquals;

import edu.wpi.first.wpilibj.RobotBase;
import my.vendor.MyVendorLibrary;

public class MyVendorLibraryTest {
  @Test
  public void whoAmI() {
    MyVendorLibrary lib = new MyVendorLibrary();
    if (RobotBase.isReal())
      assertEquals("The Robot", lib.whoAmI());
    else 
      assertEquals("A Simulation", lib.whoAmI());
  }

  @Test
  public void whoAreYou() {
    MyVendorLibrary lib = new MyVendorLibrary();
    assertEquals("The Developer", lib.whoAreYou());
  }
}