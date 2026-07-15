#!/bin/bash
set -e

echo "Generating Operations Catalog..."

echo "Compiling test sources..."
mvn -q test-compile

echo "Generating catalog..."
mvn -q exec:java -Dexec.mainClass="com.cryptoforge.model.CatalogGenerator" -Dexec.classpathScope="test"

echo "Done."
