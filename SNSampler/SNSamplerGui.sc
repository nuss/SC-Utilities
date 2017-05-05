SNSamplerGui {
	classvar <all;
	var <sampler, <window, left, top, width, height;
	var <startStopButton, <pauseResumeButton, <tempoKnob, <inputLevelKnob, <beatsPerBarNB, <numBufsNB;

	*new { |sampler, window, left, top, width, height|
		if (sampler.isNil or:{ sampler.class !== SNSampler}) {
			Error("A valid MySampler must be provided as first argument to MySamplerGui.new!").throw;
		};
		^super.newCopyArgs(sampler, window, left, top, width, height).init;
	}

	init {
		var samplerName = sampler.name.asString;
		var warningString = "The CVCenter widget '%' seems to have been removed!";

		all ?? { all = () };
		all.put(sampler.name.asSymbol, this);
		left ?? { left = 0 };
		top ?? { top = 0 };
		width ?? { width = 500 };
		height ?? { height = 400 };
		if (window.isNil or:{ window.isClosed }) {
			window = Window(sampler.name, Rect(top, left, width, height), false);
		};

		startStopButton = Button().states_([
			["start", Color.black, Color.grey],
			["stop", Color.white, Color.black]
		]).action_({ |b|
			switch (b.value,
				0, { sampler.start },
				1, { sampler.stop }
			)
		});

		pauseResumeButton = Button().states_([
			["pause", Color.white, Color.red],
			["resume", Color.black, Color.green]
		]);
		if (CVCenter.at((samplerName + "on/off").asSymbol).notNil) {
			CVCenter.at((samplerName + "on/off").asSymbol).connect(pauseResumeButton);
		} {
			warningString.format(samplerName + "on/off").warn;
		};

		tempoKnob = Knob().mode_(\vert);
		if (CVCenter.at((samplerName + "tempo").asSymbol).notNil) {
			CVCenter.at((samplerName + "tempo").asSymbol).connect(tempoKnob);
		} {
			warningString.format(samplerName + "tempo").warn;
		};

		inputLevelKnob = Knob().mode_(\vert);
		if (CVCenter.at((samplerName + "level").asSymbol).notNil) {
			CVCenter.at((samplerName + "level").asSymbol).connect(inputLevelKnob);
		} {
			warningString.format(samplerName + "level").warn;
		};

		beatsPerBarNB = NumberBox().step_(1.0).clipLo_(1).action_({ |nb|
			sampler.allocateBuffers(nb.value.asInteger);
		});

		window.layout = HLayout(
			VLayout(startStopButton, pauseResumeButton)
		);

		window.front;
	}
}