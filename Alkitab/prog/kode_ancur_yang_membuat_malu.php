<?php
	$f = fopen('b_indonesian_baru.txt', 'r');
	$i = fopen('index.txt', 'w');
	$skrg_di = -1; $sblm_di = 0;
	$pasal_offset = array(0);
	$pasal_ayat = array();
	$jml_ayat = 0;
	while($line = fgets($f)) {
		list(, $kitab, $pasal, $ayat, $isi) = explode("\t", $line);
		$skrg_di = $kitab;
		$pasal_skrg = $pasal; $ayat_skrg = $ayat;
		$ayat_offset += strlen($isi);
		
		if($pasal_skrg != $pasal_sblm || $skrg_di !== $sblm_di) {
			$pasal_offset[] = $ayat_offset;	
			$pasal_ayat[] = $jml_ayat;
			$jml_ayat = 0;
		}

		$jml_ayat++;

		if($sblm_di !== $skrg_di) {
			if($sblm_di !== 0) {
				array_pop($pasal_offset);
				fwrite($i, "Kitab nama $sblm_di judul $sblm_di file k$sblm_di.txt npasal $pasal_sblm nayat " . join(" ", $pasal_ayat) . "  pasal_offset " . join(" ", $pasal_offset) . " uda\n");
				fclose($g);
			}
			$pasal_ayat = array();
			$jml_ayat = 0;
			$pasal_offset = array(0); 
			$ayat_offset = 0;
			$g = fopen("k$skrg_di.txt", 'w');
		}
		fwrite($g, $isi);

		//echo "$kitab $pasal $ayat $isi";
		$pasal_sblm = $pasal; $ayat_sblm = $ayat;
		$sblm_di = $kitab;
	}
	$pasal_ayat[] = $jml_ayat;

	fwrite($i, "Kitab nama $kitab judul $kitab file k$skrg_di.txt npasal $pasal_sblm nayat " . join(" ", $pasal_ayat) . "  pasal_offset " . join(" ", $pasal_offset) . " uda\n");
	
	fclose($g); fclose($f); fclose($i);
