cp $1_ikon/hdpi.png ../res/drawable-hdpi/icon.png
cp $1_ikon/mdpi.png ../res/drawable/icon.png
cp $1_ikon/ldpi.png ../res/drawable-ldpi/icon.png

if [ $1 = 'tb' ] ; then
	judul='Alkitab'
	bahasa_default='in'
fi
if [ $1 = 'kjv' ] ; then
	judul='Quick KJV Bible'
	bahasa_default='en'
fi

########################
echo -n '<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">' > ../res/values/app_name.xml

echo -n $judul >> ../res/values/app_name.xml

echo '</string>
</resources>' >> ../res/values/app_name.xml

########################
echo -n '<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="pref_bahasa_default">' > ../res/values/bahasa_default.xml

echo -n $bahasa_default >> ../res/values/bahasa_default.xml

echo '</string>
</resources>' >> ../res/values/bahasa_default.xml
