#!/bin/bash

DEFAULT_VERSION="2.1.6"
VERSION=${1-$DEFAULT_VERSION}
DIR="neo4j-community-$VERSION"
FILE="$DIR-unix.tar.gz"

if [[ ! -d lib/$DIR ]]; then
    wget http://dist.neo4j.org/$FILE
    tar xvfz $FILE &> /dev/null
    rm $FILE
    [[ ! -d lib ]] && mkdir lib
        mv $DIR lib/
    [[ -h lib/neo4j ]] && unlink lib/neo4j
        ln -fs $DIR lib/neo4j
fi
