# environment vars that needs to be defined before running this
#
# ALKITAB_PROPRIETARY_DIR = directory where the proprietary (non-opensourced) resources are located
#
# SIGN_KEYSTORE = where the keystore is
# SIGN_ALIAS = key alias
# SIGN_PASSWORD = (string)

SUPER_PROJECT_NAME=androidbible
MAIN_PROJECT_NAME=Alkitab

#############################################

THIS_SCRIPT_FILE=$(cd `dirname "${BASH_SOURCE[0]}"` && pwd)/`basename "${BASH_SOURCE[0]}"`
THIS_SCRIPT_DIR=`dirname $THIS_SCRIPT_FILE`

if [ "$ALKITAB_PROPRIETARY_DIR" == "" ] ; then
	echo 'ALKITAB_PROPRIETARY_DIR not defined'
	exit 1
fi

if [ "$SIGN_KEYSTORE" == "" -o \! -r "$SIGN_KEYSTORE" ] ; then
	echo 'SIGN_KEYSTORE not defined or not readable'
	exit 1
fi

if [ "$SIGN_ALIAS" == "" ] ; then
	echo 'SIGN_ALIAS not defined'
	exit 1
fi

if [ "$SIGN_PASSWORD" == "" ] ; then
	echo 'SIGN_PASSWORD not defined'
	exit 1
fi

if [ \! \( -d "$MAIN_PROJECT_NAME" \) ] ; then
	echo "Must be run from $SUPER_PROJECT_NAME dir, which contains $MAIN_PROJECT_NAME, etc directories"
	exit 1
fi

# get the value of an xml attribute (of arbritrary tag)
get_attr() {
	FILE="$1"
	ATTR="$2"
	cat "$FILE" | grep -E "$ATTR=\"[^\"]*\"" | sed -E "s/^.*$ATTR=\"([^\"]*)\".*$/\1/"
}

# replace '0000000' on the specified filename with the last commit hash of the git repo
write_last_commit_hash() {
	FILE="$1"
	LAST_COMMIT_HASH=`git log -1 --format='format:%h'`
	echo 'Setting last commit hash: '$LAST_COMMIT_HASH' to '$FILE
	sed -i '' "s/0000000/$LAST_COMMIT_HASH/g" "$FILE"
}

overlay() {
	P_SRC="$1"
	P_DST="$2"

	SRC="$THIS_SCRIPT_DIR/build/overlay/$PKGDIST/$P_SRC"
	DST="$BUILD_MAIN_PROJECT_DIR/$P_DST"

	echo "Overlaying $P_DST with $P_SRC..."

	if [ \! -e `dirname "$DST"` ] ; then
		echo 'Making dir for overlay destination...'
		mkdir -p "`dirname "$DST"`"
	fi

	cp "$SRC" "$DST" || read
}

# START BUILD-SPECIFIC

if [ "$BUILD_PACKAGE_NAME" == "" ] ; then
	echo 'BUILD_PACKAGE_NAME not defined'
	exit 1
fi

if [ "$BUILD_DIST" == "" ] ; then
	echo 'BUILD_DIST not defined'
	exit 1
fi

# END BUILD-SPECIFIC


echo 'Creating 500 MB ramdisk...'

BUILD_NAME=$SUPER_PROJECT_NAME-build-`date "+%Y%m%d-%H%M%S"`
diskutil erasevolume HFS+ $BUILD_NAME `hdiutil attach -nomount ram://1024000`

BUILD_DIR=/Volumes/$BUILD_NAME

echo 'Build dir:' $BUILD_DIR

if [ ! -d $BUILD_DIR ] ; then
	echo 'Build dir not mounted correctly'
	exit 1
fi

echo 'Copying yuku-android-util...'
mkdir $BUILD_DIR/yuku-android-util
cp -R ../yuku-android-util/ $BUILD_DIR/yuku-android-util/

echo "Copying $SUPER_PROJECT_NAME..."
mkdir $BUILD_DIR/$SUPER_PROJECT_NAME
cp -R ./ $BUILD_DIR/$SUPER_PROJECT_NAME/

echo 'Going to' $BUILD_DIR/$SUPER_PROJECT_NAME
pushd $BUILD_DIR/$SUPER_PROJECT_NAME

	BUILD_MAIN_PROJECT_DIR=$BUILD_DIR/$SUPER_PROJECT_NAME/$MAIN_PROJECT_NAME

	pushd $MAIN_PROJECT_NAME

		# START BUILD-SPECIFIC

		PKGDIST="$BUILD_PACKAGE_NAME-$BUILD_DIST"

		echo '========================================='
		echo 'Build Config for THIS build:'
		echo '  BUILD_PACKAGE_NAME    = ' $BUILD_PACKAGE_NAME
		echo '  BUILD_DIST            = ' $BUILD_DIST
		echo '  PKGDIST               = ' $PKGDIST
		echo '========================================='

		echo 'Replacing package name in AndroidManifest.xml...'
		sed -i '' 's/package="yuku.alkitab"/package="'$BUILD_PACKAGE_NAME'"/' AndroidManifest.xml

		echo 'Replacing R references in Java files...'
		find src/ -name '*.java' -exec sed -i '' 's/import yuku.alkitab.R/import '$BUILD_PACKAGE_NAME'.R/g' {} \; 

		if [ "$BUILD_PACKAGE_NAME" == "yuku.alkitab.beta" ] ; then
			echo 'Replacing provider name for BETA version...'
			sed -i '' 's/android:authorities="yuku.alkitab.provider"/android:authorities="yuku.alkitab.provider.beta"/' AndroidManifest.xml
			sed -i '' 's/AUTHORITY = "yuku.alkitab.provider"/AUTHORITY = "yuku.alkitab.provider.beta"/' src/yuku/alkitab/base/cp/Provider.java
		fi

		echo 'Removing (mockedup) res/raw...'
		rm -rf res/raw

		TEXT_RAW="$ALKITAB_PROPRIETARY_DIR/overlay/$BUILD_PACKAGE_NAME/text_raw/"
		mkdir res/raw
		echo "Copying text overlay from $TEXT_RAW..."
		if ! cp -R $TEXT_RAW res/raw ; then
			echo 'Copy text overlay FAILED'
			exit 1
		fi

		echo "Overlaying files from $PKGDIST..."
		overlay 'analytics_trackingId.xml' 'res/values/analytics_trackingId.xml'
		overlay 'app_config.xml' 'res/xml/app_config.xml'
		overlay 'app_name.xml' 'res/values/app_name.xml'
		overlay 'pref_language_default.xml' 'res/values/pref_language_default.xml'
		overlay 'drawable-mdpi/ic_launcher.png' 'res/drawable-mdpi/ic_launcher.png'
		overlay 'drawable-hdpi/ic_launcher.png' 'res/drawable-hdpi/ic_launcher.png'
		overlay 'drawable-xhdpi/ic_launcher.png' 'res/drawable-xhdpi/ic_launcher.png'

		# END BUILD-SPECIFIC

		MANIFEST_PACKAGE_NAME=`get_attr AndroidManifest.xml package`
		MANIFEST_VERSION_CODE=`get_attr AndroidManifest.xml versionCode`
		MANIFEST_VERSION_NAME=`get_attr AndroidManifest.xml versionName`

		echo '========================================='
		echo 'From AndroidManifest.xml:'
		echo '  Package name    = ' $MANIFEST_PACKAGE_NAME
		echo '  Version code    = ' $MANIFEST_VERSION_CODE
		echo '  Version name    = ' $MANIFEST_VERSION_NAME
		echo ''
		echo 'SIGN_KEYSTORE   = ' $SIGN_KEYSTORE
		echo 'SIGN_ALIAS      = ' $SIGN_ALIAS
		echo 'SIGN_PASSWORD   = ' '.... =)'
		echo '========================================='

		if [ -e res/values/last_commit.xml ] ; then
			write_last_commit_hash res/values/last_commit.xml
		fi

		ant clean
		ant release

		if [ \! -r $BUILD_MAIN_PROJECT_DIR/bin/$MAIN_PROJECT_NAME-release-unsigned.apk ] ; then
			echo $BUILD_MAIN_PROJECT_DIR/bin/$MAIN_PROJECT_NAME-release-unsigned.apk ' not found. '
			echo 'Ant FAILED'
			exit 1
		fi

		jarsigner -keystore "$SIGN_KEYSTORE" -storepass "$SIGN_PASSWORD" -keypass "$SIGN_PASSWORD" -signedjar $BUILD_MAIN_PROJECT_DIR/bin/$MAIN_PROJECT_NAME-release-signed.apk $BUILD_MAIN_PROJECT_DIR/bin/$MAIN_PROJECT_NAME-release-unsigned.apk "$SIGN_ALIAS"

		if [ \! -r $BUILD_MAIN_PROJECT_DIR/bin/$MAIN_PROJECT_NAME-release-signed.apk ] ; then
			echo $BUILD_MAIN_PROJECT_DIR/bin/$MAIN_PROJECT_NAME-release-signed.apk ' not found. '
			echo 'Sign FAILED'
			exit 1
		fi

		zipalign 4 $BUILD_MAIN_PROJECT_DIR/bin/$MAIN_PROJECT_NAME-release-signed.apk $BUILD_MAIN_PROJECT_DIR/bin/$MAIN_PROJECT_NAME-release-signed-aligned.apk

		if [ \! -r $BUILD_MAIN_PROJECT_DIR/bin/$MAIN_PROJECT_NAME-release-signed-aligned.apk ] ; then
			echo $BUILD_MAIN_PROJECT_DIR/bin/$MAIN_PROJECT_NAME-release-signed-aligned.apk ' not found. '
			echo 'zipalign FAILED'
			exit 1
		fi

		OUTPUT=$BUILD_DIR/$MAIN_PROJECT_NAME-$MANIFEST_VERSION_CODE-$MANIFEST_VERSION_NAME-$PKGDIST.apk
		mv $BUILD_MAIN_PROJECT_DIR/bin/$MAIN_PROJECT_NAME-release-signed-aligned.apk "$OUTPUT"
		echo 'BUILD SUCCESSFUL. Output:' $OUTPUT

	popd
popd











