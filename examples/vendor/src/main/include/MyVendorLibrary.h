#pragma once

#include <string>

class MyVendorLibrary {
 public:
  std::string WhoAmI();    // This function differs between robot and simulation
  std::string WhoAreYou(); // This function is the same between robot and simulation
};