# run from v1 directory

rm -rf preview-black
mkdir preview-black

pushd data
	for i in *.zip ; do 
		cp "$i" /tmp/"$i"
		rm -rf /tmp/"$i"-extract
		mkdir /tmp/"$i"-extract
		unzip /tmp/"$i" -d /tmp/"$i"-extract/
		convert -size 384x84 -background transparent -fill black -font /tmp/"$i"-extract/"${i//.zip/}"-Regular.ttf -gravity west label:"${i//.zip/}" ../preview-black/"${i//.zip/}"-384x84.png 
	done
popd
