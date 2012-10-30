if [ ! -f .project ] ; then
	echo 'harus dijalankan dari root project Alkitab'
	exit
fi

if [ `basename $(pwd)` != 'Alkitab' ] ; then
	echo 'harus dijalankan dari root project Alkitab'
	exit
fi

`dirname $0`/kopi_ikon_dan_app_name.sh kjv
pushd res/raw
rm *
cp ../../../../alkitab-android.lama/AlkitabConverter/bahan/en-kjv-thml/raw/* .
popd

grep 'yuku\.alkitab\.kjv' AndroidManifest.xml > /dev/null || {
	echo 'ANDROID MANIFEST belum pakai paket kjv!'
	echo 'Harap pakai Rename Application Package dan ganti ke yuku.alkitab.kjv'
}
