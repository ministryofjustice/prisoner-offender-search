#!/bin/bash

# Look through all the pre authorize statements
AUTHORIZE_ROLES=$(grep -hr @PreAuthorize src/main |
  # separating on single quote we print out the roles e.g.
  # @PreAuthorize("hasRole('PRISONER_INDEX')") will print PRISONER_INDEX
  awk -F\' '{ for (i = 2; i <= NF; i=i+2) { print $i }}' |
  # ensure ROLE prefix is present on all roles
  sed -e 's/ROLE_//' -e 's/^/ROLE_/' |
  sort -u)

# Check that each role is documented
DOCUMENTED_ROLES=$(awk '/     ROLE_/ { sub("[.]", "", $1); print $1 }' src/main/resources/documentation/service-description.html |
  sort -u)

if ! DIFF_RESULT=$(diff <(echo "$AUTHORIZE_ROLES") <(echo "$DOCUMENTED_ROLES")); then
  printf "Found difference of:\n%s\nin authorised roles and documented roles.\n" "$DIFF_RESULT"
  printf "Lines in src/main that contain @PreAuthorize:\n%s\n\n" "$AUTHORIZE_ROLES"
  printf "Lines in src/main/resources/documentation/service-description.html that start with '     ROLE_':\n%s\n\n" "$DOCUMENTED_ROLES"
  printf "This check tries to ensure that all roles in use are documented.\n"
  exit 1
fi
