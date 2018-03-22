#!/bin/sh -e
#

printUsage() {
   echo "Usage : "
   echo "./compile.sh [-s | --skipTests] SHA1"
   echo "    -s : Skip test"
   echo "    SHA1: SHA1 to build (optional)"
   exit 1
}

ORIGIN=/origin
DESTINATION=/destination

for arg in "$@"
do
   case $arg in
      -s|--skipTests)
         SKIPTESTS="skipTests"
         ;;
      -*)
         echo "Invalid option: -$OPTARG"
         printUsage
         ;;
      *)
         if ! [ -z "$1" ]; then
            SHA1=$1
         fi
         ;;
   esac
   if [ "0" -lt "$#" ]; then
      shift
   fi
done

if [ -z "$SHA1" ]; then
   SHA1=master
fi

# Sources retrieval
git clone $ORIGIN/.
git checkout $SHA1

# Compilation

if [ "$SKIPTESTS" = "skipTests" ]; then
   mvn package -DskipTests -Pjpa,lucene,jpa-lucene,with-assembly
else
   mvn package -Pjpa,lucene,jpa-lucene,with-assembly
fi

# Retrieve result

if [ $? -eq 0 ]; then
   cp server/app/target/james-server-app-*-app.zip $DESTINATION
fi
