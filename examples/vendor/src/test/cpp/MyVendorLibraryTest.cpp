#include <gtest/gtest.h>

#include "MyVendorLibrary.h"

TEST(MyVendorLibrary, WhoAreYou) {
  MyVendorLibrary lib;
  ASSERT_EQ(lib.WhoAreYou(), "The Developer");
}

TEST(MyVendorLibrary, WhoAmI) {
  MyVendorLibrary lib;
#ifdef __FRC_ROBORIO__
  ASSERT_EQ(lib.WhoAmI(), "The Robot");
#else
  ASSERT_EQ(lib.WhoAmI(), "A Simulation");
#endif
}