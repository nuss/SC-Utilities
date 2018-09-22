CSSColorTranslator {
	classvar <>colorNameTable;

	*initClass {
		this.colorNameTable = CSVFileReader.read(this.filenameSymbol.asString.dirname +/+
			"css_color_names_translation_table.csv", true).flatten(1).collect{ |w| w.trim.toLower }.as(Dictionary);
	}

	*translate { |name|
		^this.colorNameTable[name.asString.toLower]
	}

}

+Color {

	*fromHexOrName { |color|
		color = color.asString.toLower;
		if (CSSColorTranslator.colorNameTable.keys.includes(color)) {
			^Color.fromHexString(CSSColorTranslator.colorNameTable[color])
		} {
			^Color.fromHexString(color)
		}
	}

}
