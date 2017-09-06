#!/bin/sh
set -e
export TAG=${TRAVIS_TAG:-latest}
export VERSION=${TRAVIS_TAG:-latest}

./gradlew build -i -x test

./gradlew pack buildImage tagImage pushImage -x test
./systemtests/scripts/run_test_component.sh templates/install /tmp/openshift systemtests
./.travis/generate-bintray-descriptor.sh enmasse templates/build/enmasse-${TAG}.tgz > .bintray.json
