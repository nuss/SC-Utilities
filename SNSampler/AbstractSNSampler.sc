AbstractSNSampler {
	classvar <>synthDescLib = \SN;

	// handle widget creation in CVCenter
	cvCenterAddWidget { |suffix="", value, spec, func, midiMode, softWithin|
		CVCenter.all[(this.name.asString ++ suffix).asSymbol] ?? {
			CVCenter.use(this.name.asString ++ suffix, spec, value, this.name);
			if (func.class == String) {
				func = func.interpret;
			};
			if (func.isFunction) {
				CVCenter.addActionAt(this.name.asString ++ suffix, suffix, func);
			}
		};
		midiMode !? { CVCenter.cvWidgets[(this.name.asString ++ suffix).asSymbol].setMidiMode(midiMode) };
		softWithin !? { CVCenter.cvWidgets[(this.name.asString ++ suffix).asSymbol].setSoftWithin(softWithin) };
	}
}