CSSColorTranslator {
	classvar <>colorNameTable;

	*initClass {
		this.colorNameTable = CSVFileReader.read(this.filenameSymbol.asString.dirname +/+
			"css_color_names_translation_table.csv", true).flatten(1).collect{ |w| w.trim.toLower }.as(Dictionary);
	}

}

+Color {

	*fromHexOrName { |color|
		if (CSSColorTranslator.colorTableName.keys.includes(color.asString.toLower)) {
			^Color.fromHexString(CSSColorTranslator.colorNameTable[color.asString.toLower])
		} {
			^Color.fromHexString(color.asString.toLower)
		}
	}

}
cd