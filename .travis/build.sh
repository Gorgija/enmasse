#!/bin/sh
set -e
export VERSION=${TRAVIS_TAG:-latest}

if [ "$VERSION" != "latest" ]; then
    export TAG=$VERSION
fi

if [ "$TRAVIS_BRANCH" != "master" ] || [ "$TRAVIS_PULL_REQUEST" != "false" ]
then
    export DOCKER_REGISTRY="172.30.1.1:5000"
    export DOCKER_ORG=enmasseci
fi

echo "Building EnMasse"
make

echo "Tagging Docker Images"
make docker_tag

if [ "$TRAVIS_BRANCH" != "master" ] || [ "$TRAVIS_PULL_REQUEST" != "false" ]
then
    echo "Logging into to local docker registry"
    oc new-project enmasseci
    docker login -u enmasseci -p `oc whoami -t` 172.30.1.1:5000
else
    echo "Logging in to Docker Hub"
    docker login -u $DOCKER_USER -p $DOCKER_PASS
fi

echo "Pushing images to Docker Registry"
make docker_push

echo "Running systemtests"
./systemtests/scripts/run_test_component.sh templates/install systemtests

echo "Generating bintray artifact descriptor"
./.travis/generate-bintray-descriptor.sh enmasse templates/build/enmasse-${VERSION}.tgz > .bintray.json
