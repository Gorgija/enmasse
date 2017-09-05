#!/bin/bash
VERSION=${TRAVIS_TAG:-latest}
if [ "$VERSION" != "latest" ]; then
    TAG=$VERSION
fi

PACKAGE=enmasse
REPOSITORY="snapshots"
if [ -n "$TRAVIS_TAG" ]
then
    REPOSITORY="releases"
    TRAVIS_BUILD_NUMBER="."
fi

cat<<EOF
{
    "package": {
        "name": "${PACKAGE}",
        "repo": "${REPOSITORY}",
        "subject": "enmasse",
        "desc": "${PACKAGE} built by travis",
        "website_url": "enmasseproject.github.io",
        "issue_tracker_url": "https://github.com/EnMasseProject/${PACKAGE}/issues",
        "vcs_url": "https://github.com/EnMasseProject/${PACKAGE}.git",
        "github_use_tag_release_notes": false,
        "licenses": ["Apache-2.0"],
        "public_download_numbers": true,
        "public_stats": true 
    },

    "version": {
        "name": "${TAG}"
    },

    "files": [
            {"includePattern": "target/surefire-reports/(.*)", "uploadPattern":"$TRAVIS_BUILD_NUMBER/test-reports/\$1", "matrixParams": {"override": 1}},
            {"includePattern": "target/site/(surefire-report.html)", "uploadPattern":"$TRAVIS_BUILD_NUMBER/test-reports/\$1", "matrixParams": {"override": 1}},
            {"includePattern": "templates/build/(enmasse-${VERSION}.tgz)", "uploadPattern":"$TRAVIS_BUILD_NUMBER/\$1", "matrixParams": {"override": 1}}
    ],
    "publish": true
}
EOF
