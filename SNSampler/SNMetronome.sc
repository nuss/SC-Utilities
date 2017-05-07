SNMetronome {
	classvar <all;
	var <name, <clock, <tempo, <beatsPerBar, <server;
	var pdef;

	*new { |name, clock, tempo=1, beatsPerBar=4, server, assocName, out=0, numChannels=1, amp=1|
		^super.newCopyArgs(name, clock, tempo, beatsPerBar, server).init(assocName, out, numChannels, amp);
	}

	init { |assocName, out, numChannels, amp|
		all ?? { all = () };
		if (name.isNil) {
			name = ("metronome" + (all.size + 1)).asSymbol;
		} {
			if (all.includesKey(name.asSymbol)) {
				Error("A metronome under the given name already exists").throw;
			}
		};
		all.put(name.asSymbol, this);
		clock ?? {
			// create a new clock, compensating latency
			clock = TempoClock(tempo, 0, server.latency.neg);
		};

		server.bind {
			SynthDescLib.at(\snSynthDefs) ?? {
				// store the metronome in a separate SynthDescLib in order to avoid name clashes
				SynthDescLib(\snSynthDefs, [server]);
			};
			SynthDef(name.asSymbol, {
				var env = EnvGen.ar(Env.perc(0.001, 0.1), doneAction: 2);
				Out.ar(\out.kr(out), SinOsc.ar(\freq.kr(330) ! numChannels, mul: env * \baseAmp.kr * \amp.kr(amp)));
			}).add(\snSynthDefs);
			server.sync;
			pdef = this.schedule;
			pdef.play(clock);
			// add a CVWidget for pausing/resuming the metronome
			CVCenter.use(
				(name.asString + "metro on/off").asSymbol,
				#[0, 1, \lin, 1.0],
				0,
				assocName
			);
			// if handled via midi we will want midiMode to be 0 (0-127) and no softWithin
			CVCenter.cvWidgets[(name.asString + "metro on/off").asSymbol]
			.setMidiMode(0).setSoftWithin(0);
			CVCenter.addActionAt(
				(name.asString + "metro on/off").asSymbol,
				"pause/resume metronome",
				{ |cv|
					if (cv.input > 0) {
						// make sure metronome and sampler are in sync
						this.schedule;
						Pdef(name.asSymbol).resume;
					} { Pdef(name.asSymbol).pause };
				}
			)
		};
	}

	clear {
		Pdef(name).clear;
		all.removeAt(name);
	}

	schedule { |post=false, out=0, amp=0, tabName|
		tabName ?? {
			tabName = name.asSymbol;
		};
		if (post) {
			^Pdef(tabName,
				Pbind(
					\synthLib, SynthDescLib.all[\metronomes],
					\instrument, name.asSymbol,
					\freq, Pseq([440] ++ (330 ! (beatsPerBar - 1)), inf),
					\dur, 1,
					\baseAmp, Pseq([1] ++ (0.7 ! (beatsPerBar - 1)), inf),
					\amp, CVCenter.use((name.asString + "metroAmp").asSymbol, value: amp, tab: tabName),
					\out, out,
					\trace, Pfunc {  "metronome" + name + "beat:" + clock.beatInBar }.trace
				)
			).quant_([beatsPerBar, 0, 0, 1])
		} {
			^Pdef(tabName,
				Pbind(
					\synthLib, SynthDescLib.all[\metronomes],
					\instrument, name.asSymbol,
					\freq, Pseq([440] ++ (330 ! (beatsPerBar - 1)), inf),
					\dur, 1,
					\baseAmp, Pseq([1] ++ (0.7 ! (beatsPerBar - 1)), inf),
					\amp, CVCenter.use((name.asString + "metroAmp").asSymbol, value: amp, tab: tabName),
					\out, out
				)
			).quant_([beatsPerBar, 0, 0, 1])
		}
	}
}