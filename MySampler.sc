MySampler {
	classvar <all, <synthDescLib;
	var <name, <clock, <tempo, <beatsPerBar, <numBuffers, <server, <inBus;
	var <buffers, <recorder, <metronome;
	var recorder, <isPlaying = false;
	var <synthDescLib;
	var nameString;

	*new { |name, clock, tempo=1, beatsPerBar=4, numBuffers=5, server|
		^super.newCopyArgs(name, clock, tempo, beatsPerBar, numBuffers).init;
	}

	init {
		all ?? { all = () };
		if (name.isNil) {
			name = ("sampler" + (all.size + 1)).asSymbol
		} {
			if (all.includesKey(name.asSymbol)) {
				Error("A sampler under the given name already exists").throw;
			}
		};
		all = all.put(name.asSymbol, this);
		nameString = name.asString;

		server ?? { server = Server.default };
		clock ?? {
			// create a new clock, compensating latency
			clock = TempoClock(tempo, 0, server.latency.neg);
		};

		// boot the server before proceeding (if the server isn't already booted)
		server.waitForBoot {
			buffers = Buffer.allocConsecutive(
				numBuffers,
				server,
				(server.sampleRate * beatsPerBar).asInteger
			);
			recorder = NodeProxy(server, \audio, 1).clock_(clock).quant_([beatsPerBar, 0, 0, 1]);
			recorder[0] = {
				var soundIn = SoundIn.ar(\in.kr(inBus), \inputLevel.kr(0.5));
				BufWr.ar(
					soundIn,
					\bufnum.kr(0),
					// Phasor will ramp from start to end and then jump back to start
					// if tempo != 1 we have to move through the buffer at a different rate
					Phasor.ar(0, BufRateScale.kr(\bufnum.kr(0), tempo), 0, BufFrames.kr(\bufnum.kr(0)))
				);
				// play silently
				0.0;
			};
			this.schedule;
			CVCenter.use((nameString + "on/off").asSymbol, #[0, 1, \lin, 1.0], 0, name);
			// if handled via midi we will want midiMode to be 0 (0-127) and no softWithin
			CVCenter.cvWidgets[(nameString + "on/off").asSymbol]
			.setMidiMode(0).setSoftWithin(0);
			CVCenter.addActionAt(
				(nameString + "on/off").asSymbol,
				"pause/resume",
				{ |cv| if (cv.value == 1) { this.resume } { this.pause }}
			);
			this.addMetronome(out: 0, numChannels: 1, amp: 0);
		}
	}

	start {
		recorder.play;
		isPlaying = true;
	}

	stop {
		recorder.stop;
		isPlaying = false;
	}

	pause {
		recorder.pause;
	}

	resume {
		recorder.resume;
	}

	// schedule sampling, post current off beat if post == true
	schedule { |post=false|
		"beatsPerBar: %\n".postf(beatsPerBar);
		if (post) {
			recorder[1] = \set -> Pbind(
				\dur, beatsPerBar,
				\in, CVCenter.use(
					(nameString + "in").asSymbol,
					ControlSpec(0, server.options.firstPrivateBus-1, \lin, 1.0),
					tab: name
				),
				\inputLevel, CVCenter.use(
					(nameString + "level").asSymbol,
					\amp,
					0.5,
					name
				),
				\bufnum, Pseq(buffers.collect(_.bufnum), inf),
				\tempo, CVCenter.use(
					nameString + "tempo",
					#[0.2, 4, \lin],
					tempo,
					name
				),
				\beat, Pfunc { nameString + "beat:" + clock.beatInBar }.trace
			);

		} {
			recorder[1] = \set -> Pbind(
				\dur, beatsPerBar,
				\in, CVCenter.use(
					(nameString + "in").asSymbol,
					ControlSpec(0, server.options.firstPrivateBus-1, \lin, 1.0),
					tab: name
				),
				\inputLevel, CVCenter.use(
					(nameString + "level").asSymbol,
					\amp,
					0.5,
					name
				),
				\bufnum, Pseq(buffers.collect(_.bufnum), inf),
				\tempo, CVCenter.use(
					nameString + "tempo",
					#[0.2, 4, \lin],
					tempo,
					name
				)
			);
		}
	}

	// an acoustic metronome
	addMetronome { |out, numChannels, amp|
		metronome ?? {
			metronome = MyMetronome(
				name, clock, tempo, beatsPerBar, server,
				name, out, numChannels, amp
			)
		}
	}

	removeMetronome {
		metronome.clear;
		metronome = nil;
	}
}