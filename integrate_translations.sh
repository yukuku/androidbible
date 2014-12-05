if [ "$1" == "" ] ; then
	echo 'Usage: this_script <directory of translations>'
	exit 1
fi

DST="Alkitab/src/main/res/"
SRC="$1"

if [ ! -e "$DST" ] ; then
	echo "This script not run from the correct directory"
	exit 1
fi

if [ ! -e "$SRC" ] ; then
	echo "Source dir not found"
	exit 1
fi

# -----------------------------------------------------------vvvvv!! 
PAIRS=(af af cs cs de de es es-ES fr fr in id ja ja lv lv ms ms nl nl pl pl pt pt-BR ro ro ru ru uk uk zh-rCN zh-CN zh-rTW zh-TW)

for ((i=0; i<${#PAIRS[@]}; i+=2)) ; do
	DSTLANG="${PAIRS[$i]}"
	SRCLANG="${PAIRS[$((i+1))]}"
	DSTSUBDIR="$DST/values-$DSTLANG"
	SRCSUBDIR="$SRC/$SRCLANG"

	if [ ! -e "$DSTSUBDIR" ] ; then
		echo "Dest sub dir not found: $DSTSUBDIR"
		exit 1
	fi

	if [ ! -e "$SRCSUBDIR" ] ; then
		echo "Source sub dir not found: $SRCSUBDIR"
		exit 1
	fi

	cp -v -R "$SRCSUBDIR/" "$DSTSUBDIR"

done

