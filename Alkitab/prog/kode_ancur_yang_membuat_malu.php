<?php
	$fn = fopen('tb_nama.txt', 'rb');
	$nama_kitab = array();
	while ($line = fgets($fn)) {
		list($no, $judul) = explode("\t", $line);
		$no = (int)$no;
		$nama_kitab[$no] = trim($judul);
	}
	fclose($fn);
	
	$f = fopen('b_indonesian_baru.txt', 'rb');
	$i = fopen('tb_index.txt', 'wb');
	
	
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
	
	$g = fopen(sprintf("tb_k%02d.txt", $kitab), 'wb');
	
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
			jumlah($nama_kitab[$kitab], sprintf("tb_k%02d", $kitab), count($nayat), $nayat, $pasal_offset);
			$kitab = $inkitab;
			$pasal = 1;
			$nayat = array();
			$pasal_offset = array(0);
			$maju = 0;
			
			// tutup
			fclose($g);
			
			// buka baru
			$g = fopen(sprintf("tb_k%02d.txt", $kitab), 'wb');
		}
		
		$c++;
		$maju += strlen($isi);
		
		fwrite($g, $isi);
	}
	
	$nayat[] = $c;
	//$pasal_offset[] = $maju;
	
	jumlah($nama_kitab[$kitab], sprintf("tb_k%02d", $kitab), $pasal, $nayat, $pasal_offset);
	// tutup
	fclose($g);
	
	fclose($f); 
	fclose($i);
