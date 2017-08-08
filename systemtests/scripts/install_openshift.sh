#!/bin/bash
set -x
DEST=$1
mkdir -p $DEST
wget https://github.com/openshift/origin/releases/download/v3.6.0/openshift-origin-server-v3.6.0-c4dd4cf-linux-64bit.tar.gz -O openshift.tar.gz
tar xzf openshift.tar.gz -C $DEST --strip-components 1
export PATH="$PATH:$DEST"
