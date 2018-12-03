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
		var isValidHexString;
		color = color.asString.toLower;
		isValidHexString = "#[0-9a-fA-F]{6}".matchRegexp(color);

		if (CSSColorTranslator.colorNameTable.keys.includes(color)) {
			^this.fromHexString(CSSColorTranslator.colorNameTable[color])
		} {
			if (isValidHexString) {
				^this.fromHexString(color);
			} { ^nil }
		}
	}

}
