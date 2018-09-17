<?php
	if (count($argv) < 5) {
?>
	// contoh pemakaian: 
	// php prog/bdb_to_res_raw.php bahan/en-kjv-thml/kjv3_teks_bdb.txt bahan/en-kjv-thml/kjv_index.txt kjv bahan/en-kjv-thml/kjv_raw/ bahan/en-kjv-thml/kjv_kitab.txt
	//            [0]              [1] (input)                         [2] (output)                    [3] [4] (output)               [5] (input)
<?php 		
		die;	
	}
	
	$fn = fopen($argv[5], 'rb');
	$nama_kitab = array();
	$no = 1;
	while ($line = fgets($fn)) {
		$nama_kitab[$no] = trim($line);
		$no++;
	}
	
	fclose($fn);
	error_reporting(E_ALL);
	
	$f = fopen($argv[1], 'rb');
	$i = fopen($argv[2], 'wb');
	
	function jumlah($judul, $file, $npasal, $nayat, $pasal_offset) {
		global $i;
		fwrite($i, "Kitab nama $judul judul $judul file $file npasal $npasal nayat " . join(" ", $nayat) . "  pasal_offset " . join(" ", $pasal_offset) . " uda\n");
	}
	
	$kitab = 1;
	$pasal = 1;
	$nayat = array();
	$pasal_offset = array(0);
	$maju = 0;
	$c = 0;
	
	$g = fopen(sprintf($argv[4] . $argv[3] . "_k%02d.txt", $kitab), 'wb');
	
	while($line = fgets($f)) {
		list(, $inkitab, $inpasal, , $isi) = explode("\t", $line);
		
		if ($inpasal != $pasal or $inkitab != $kitab) { // end of pasal
			$nayat[] = $c;
			if ($inkitab == $kitab) {
				$pasal_offset[] = $maju;
			}
			$c = 0;
			$pasal = $inpasal;
		}
		
		if ($inkitab != $kitab) { // end of kitab
			jumlah($nama_kitab[$kitab], sprintf($argv[3] . "_k%02d", $kitab), count($nayat), $nayat, $pasal_offset);
			$kitab = $inkitab;
			$pasal = 1;
			$nayat = array();
			$pasal_offset = array(0);
			$maju = 0;
			
			// tutup
			fclose($g);
			
			// buka baru
			$nf = sprintf($argv[4] . $argv[3] . "_k%02d.txt", $kitab);
			echo $nf . "\n";
			$g = fopen($nf, 'wb');
		}
		
		$c++;
		$maju += strlen($isi);
		
		fwrite($g, $isi);
	}
	
	$nayat[] = $c;
	//$pasal_offset[] = $maju;
	
	jumlah($nama_kitab[$kitab], sprintf($argv[3] . "_k%02d", $kitab), $pasal, $nayat, $pasal_offset);
	// tutup
	fclose($g);
	
	fclose($f); 
	fclose($i);
