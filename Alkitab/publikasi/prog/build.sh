# environment vars that needs to be defined before running this
#
# ALKITAB_RAW_DIR = directory where the actual res/raw content (the non-free text, etc)
# ALKITAB_VERSION_CODE = (int)
# ALKITAB_VERSION_NAME = (string)
# ALKITAB_PACKAGE_NAME = (string)
# ALKITAB_STABLE = (int) 1 is normal release
# ALKITAB_SIGN_KEYSTORE = where the keystore is
# ALKITAB_SIGN_ALIAS = key alias
# ALKITAB_SIGN_PASSWORD = (string)


if [ \! \( -d Alkitab -a -d AlkitabYes -a -d bibleplus -a -d ../yuku-android-util \) ] ; then
	echo 'Must be run from androidbible dir, which contains Alkitab, AlkitabYes, etc directory'
	echo 'and has yuku-android-util at the parent'
	exit 1
fi

if [ "$ALKITAB_RAW_DIR" == "" ] ; then
	echo 'ALKITAB_RAW_DIR not defined'
	exit 1
fi

if [ "$ALKITAB_VERSION_CODE" == "" ] ; then
	echo 'ALKITAB_VERSION_CODE not defined'
	exit 1
fi

if [ "$ALKITAB_VERSION_NAME" == "" ] ; then
	echo 'ALKITAB_VERSION_NAME not defined'
	exit 1
fi

if [ "$ALKITAB_PACKAGE_NAME" == "" ] ; then
	echo 'ALKITAB_PACKAGE_NAME not defined'
	exit 1
fi

if [ "$ALKITAB_STABLE" == "" ] ; then
	echo 'ALKITAB_STABLE not defined'
	exit 1
fi

if [ "$ALKITAB_SIGN_KEYSTORE" == "" -o \! -r "$ALKITAB_SIGN_KEYSTORE" ] ; then
	echo 'ALKITAB_SIGN_KEYSTORE not defined or not readable'
	exit 1
fi

if [ "$ALKITAB_SIGN_ALIAS" == "" ] ; then
	echo 'ALKITAB_SIGN_ALIAS not defined'
	exit 1
fi

if [ "$ALKITAB_SIGN_PASSWORD" == "" ] ; then
	echo 'ALKITAB_SIGN_PASSWORD not defined'
	exit 1
fi

echo '========================================='
echo 'ALKITAB_RAW_DIR       = ' $ALKITAB_RAW_DIR
echo 'ALKITAB_VERSION_CODE  = ' $ALKITAB_VERSION_CODE
echo 'ALKITAB_VERSION_NAME  = ' $ALKITAB_VERSION_NAME
echo 'ALKITAB_PACKAGE_NAME  = ' $ALKITAB_PACKAGE_NAME
echo 'ALKITAB_STABLE        = ' $ALKITAB_STABLE
echo 'ALKITAB_SIGN_KEYSTORE = ' $ALKITAB_SIGN_KEYSTORE
echo 'ALKITAB_SIGN_ALIAS    = ' $ALKITAB_SIGN_ALIAS
echo 'ALKITAB_SIGN_PASSWORD = ' '.... =)'
echo '========================================='


echo 'Creating 500 MB ramdisk...'

BUILD_NAME=alkitab-build-`date "+%Y%m%d-%H%M%S"`
diskutil erasevolume HFS+ $BUILD_NAME `hdiutil attach -nomount ram://1000000`

BUILD_DIR=/Volumes/$BUILD_NAME

echo 'Build dir:' $BUILD_DIR

if [ ! -d $BUILD_DIR ] ; then
	echo 'Build dir not mounted correctly'
	exit 1
fi

echo 'Copying yuku-android-util...'
mkdir $BUILD_DIR/yuku-android-util
cp -R ../yuku-android-util/* $BUILD_DIR/yuku-android-util/

echo 'Copying androidbible...'
mkdir $BUILD_DIR/androidbible
cp -R * $BUILD_DIR/androidbible/

echo 'Going to' $BUILD_DIR/androidbible
pushd $BUILD_DIR/androidbible

cd Alkitab

echo 'Removing (mockedup) res/raw...'
rm -rf res/raw

echo 'Copying overlay res/raw...'
if ! cp -R $ALKITAB_RAW_DIR res/ ; then
	echo 'Copy overlay FAILED'
	exit 1
fi


ant clean
ant release

if [ \! -r $BUILD_DIR/androidbible/Alkitab/bin/Alkitab-release-unsigned.apk ] ; then
	echo $BUILD_DIR/androidbible/Alkitab/bin/Alkitab-release-unsigned.apk ' not found. '
	echo 'Ant FAILED'
	exit 1
fi

jarsigner -keystore "$ALKITAB_SIGN_KEYSTORE" -storepass "$ALKITAB_SIGN_PASSWORD" -keypass "$ALKITAB_SIGN_PASSWORD" -signedjar $BUILD_DIR/androidbible/Alkitab/bin/Alkitab-release-signed.apk $BUILD_DIR/androidbible/Alkitab/bin/Alkitab-release-unsigned.apk "$ALKITAB_SIGN_ALIAS"

if [ \! -r $BUILD_DIR/androidbible/Alkitab/bin/Alkitab-release-signed.apk ] ; then
	echo $BUILD_DIR/androidbible/Alkitab/bin/Alkitab-release-signed.apk ' not found. '
	echo 'Sign FAILED'
	exit 1
fi

zipalign 4 $BUILD_DIR/androidbible/Alkitab/bin/Alkitab-release-signed.apk $BUILD_DIR/androidbible/Alkitab/bin/Alkitab-release-signed-aligned.apk

if [ \! -r $BUILD_DIR/androidbible/Alkitab/bin/Alkitab-release-signed-aligned.apk ] ; then
	echo $BUILD_DIR/androidbible/Alkitab/bin/Alkitab-release-signed-aligned.apk ' not found. '
	echo 'zipalign FAILED'
	exit 1
fi

OUTPUT=$BUILD_DIR/Alkitab-$ALKITAB_VERSION_CODE-$ALKITAB_VERSION_NAME-$ALKITAB_PACKAGE_NAME.apk
mv $BUILD_DIR/androidbible/Alkitab/bin/Alkitab-release-signed-aligned.apk "$OUTPUT"
echo 'BUILD SUCCESSFUL. Output:' $OUTPUT

popd











