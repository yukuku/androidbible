package yuku.alkitab.base.pdbconvert

object PdbBookNumberToBookIdMapping {
    /**
     * The index of each PalmBible+ book number is its book id.
     *
     * Ref: http://sourceforge.net/projects/palmbibleplus/files/zDocumentation/1.0/
     */
    private val pdbBookNumbers = intArrayOf(
        10,
        20,
        30,
        40,
        50,
        60,
        70,
        80,
        90,
        100, // 2 sam
        110,
        120,
        130,
        140, // 2 taw
        150, // ezra
        160, // nehemia
        190, // ester
        220, // job/ayub
        230, // ps
        240, // amsal
        250, // pengkotbah/ecc
        260, // kidung
        290, // yesaya
        300,
        310, // ratapan
        330, // ezekiel
        340,
        350,
        360,
        370,
        380,
        390,
        400,
        410,
        420,
        430,
        440,
        450,
        460, // mal
        470, // matius
        480,
        490,
        500,
        510,
        520,
        530,
        540,
        550,
        560,
        570,
        580,
        590,
        600,
        610,
        620,
        630,
        640,
        650,
        660,
        670,
        680,
        690,
        700,
        710,
        720,
        730, // book id 65

        145, // 1 esdras, book id 66
        146, // 2 esdras
        170, // tobit #katolik
        180, // judit #katolik
        200, // 1 makabe #katolik, book id 70
        210, // 2 makabe #katolik
        215, // 3 makabe
        216, // 4 makabe
        231, // Psalms (from Heb.) Ps (H) Vulg.: Jerome's translation from the Hebrew
        235, // Odes, book id 75
        270, // wisdom of solomon #katolik
        280, // sirach / Ecclesiasticus #katolik
        285, // Psalms of Solomon
        315, // Letter of Jeremiah
        320, // baruk #katolik, book id 80
        335, // susanna
        345, // Prayer of Azariah and the Song of the Three Jews
        346, // Bel and the Dragon
        790, // Prayer of Manasseh
        980, // Additions to Esther, book id 85
        991, // maxmur 151
        1802, // Epistle to the Laodicaeans, book id 87
    )

    /**
     * Book numbers outside the reference numbering, as found in e.g. kjvf_eng.pdb.
     *
     * Ref: email from jacobwarner@gmail.com
     * Ref: http://www.koders.com/java/fid02C34FE21E1277132EE987F8478E08A5AB9E0828.aspx?s=WhenTag
     */
    private val altBookNumberToBookId = mapOf(
        740 to 66, // 1 Esdras
        750 to 67, // 2 Esdras
        760 to 79, // Letter of Jeremiah
        770 to 82, // Prayer of Azariah
        780 to 83, // Bel and the Dragon
    )

    /**
     * Returns the book id for [pdbBookNumber], or -1 if it is not a known book number.
     */
    @JvmStatic
    fun pdbBookNumberToBookId(pdbBookNumber: Int): Int =
        pdbBookNumbers.indexOf(pdbBookNumber).takeIf { it >= 0 }
            ?: altBookNumberToBookId[pdbBookNumber]
            ?: -1
}
