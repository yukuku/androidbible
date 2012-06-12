# run from fonts directory

for i in * ; do convert -size 440x96 -background white -fill black -font $i/$i-Regular.ttf -gravity west label:"$i" $i-440x96.png ; done
