#
# replacer = "${database}"
############################

# Create the user;
CREATE USER '${user}' IDENTIFIED BY '${password}';

CREATE DATABASE ${database} DEFAULT CHARACTER SET utf8;
