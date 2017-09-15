#!/bin/bash
SUCCESS=$1
VERSION=${TRAVIS_TAG:-latest}
if [ "$VERSION" != "latest" ]; then
    TAG=$VERSION
fi

export PACKAGE=enmasse
export REPOSITORY="snapshots"
if [ -n "$TRAVIS_TAG" ]
then
    export REPOSITORY="releases"
    export TRAVIS_BUILD_NUMBER="."
fi

function upload_file() {
    local file=$1
    local target=$2
    if [ -f $file ]; then
        echo "curl -T $file -u${BINTRAY_API_USER}:${BINTRAY_API_TOKEN} -H 'X-Bintray-Package:${PACKAGE}' -H 'X-Bintray-Version:${VERSION}' https://api.bintray.com/content/enmasse/snapshots/$target"
        curl -T $file -u${BINTRAY_API_USER}:${BINTRAY_API_TOKEN} -H "X-Bintray-Package:${PACKAGE}" -H "X-Bintray-Version:${VERSION}" https://api.bintray.com/content/enmasse/snapshots/$target
    else
        echo "Skipping $file, not found"
    fi
}

function upload_folder() {
    local folder=$1
    local target=$2
    for i in `find $folder -type f`
    do
        base=`basename $i`
        upload_file $i "$target/$base"
    done
}

if [ "$SUCCESS" == "true" ]; then
    upload_file templates/build/enmasse-${VERSION}.tgz enmasse-${VERSION}.tgz
else
    echo "Collecting test reports"
    
    mkdir -p target/surefire-reports
    for i in `find . -name "TEST-*.xml"`
    do
        cp $i target/surefire-reports
    done
    mvn surefire-report:report-only

    upload_file templates/build/enmasse-${VERSION}.tgz $TRAVIS_BUILD_NUMBER/enmasse-${VERSION}.tgz
    upload_file target/site/surefire-report.html $TRAVIS_BUILD_NUMBER/test-reports/surefire-report.html
    upload_folder target/surefire-reports $TRAVIS_BUILD_NUMBER/test-reports
fi
