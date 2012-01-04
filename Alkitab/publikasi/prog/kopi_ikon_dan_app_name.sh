# harus dijalanin dari dir root project!

cp publikasi/img/$1_ikon/hdpi.png res/drawable-hdpi/icon.png
cp publikasi/img/$1_ikon/mdpi.png res/drawable-mdpi/icon.png
cp publikasi/img/$1_ikon/ldpi.png res/drawable-ldpi/icon.png

if [ $1 = 'tb' ] ; then
	judul='Alkitab'
	bahasa_default='in'
fi

if [ $1 = 'kjv' ] ; then
	judul='Quick Bible'
	bahasa_default='DEFAULT'
fi

if [ $1 = 'nrkv' ] ; then
	judul='Korean NRKV Bible'
	bahasa_default='en'
	convert kjv_ikon/hdpi.png -gravity north -stroke '#000C' -strokewidth 2 -annotate 0 'DEMO' -stroke none -fill white -annotate 0 'DEMO' ../res/drawable-hdpi/icon.png
	convert kjv_ikon/mdpi.png -gravity north -stroke '#000C' -strokewidth 2 -annotate 0 'DEMO' -stroke none -fill white -annotate 0 'DEMO' ../res/drawable-mdpi/icon.png
	convert kjv_ikon/ldpi.png -gravity north -stroke '#000C' -strokewidth 2 -annotate 0 'DEMO' -stroke none -fill white -annotate 0 'DEMO' ../res/drawable-ldpi/icon.png
fi

######################## Tulis app name
RES_APP_NAME=res/values/app_name.xml
echo -n '<?xml version="1.0" encoding="utf-8"?><resources><string name="app_name">' > $RES_APP_NAME
echo -n $judul >> $RES_APP_NAME
echo '</string></resources>' >> $RES_APP_NAME

######################## Tulis default bahasa
RES_BAHASA_DEFAULT=res/values/bahasa_default.xml
echo -n '<?xml version="1.0" encoding="utf-8"?><resources><string name="pref_bahasa_default">' > $RES_BAHASA_DEFAULT
echo -n $bahasa_default >> $RES_BAHASA_DEFAULT
echo '</string></resources>' >> $RES_BAHASA_DEFAULT
