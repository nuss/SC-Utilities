MySampler {
	classvar <all, <synthDescLib;
	var <name, <clock, <tempo, <beatsPerBar, <numBars, <numBuffers, <server, <inBus;
	var <buffers, <recorder, <metronome, buffersLoaded = false;
	var recorder, <isPlaying = false;
	var <synthDescLib;
	var nameString;

	*new { |name, clock, tempo=1, beatsPerBar=4, numBars=1, numBuffers=5, server|
		^super.newCopyArgs(name, clock, tempo, beatsPerBar, numBars, numBuffers).init;
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
			server.bind {
				buffers = Buffer.allocConsecutive(
					numBuffers,
					server,
					(server.sampleRate * beatsPerBar * numBars).asInteger
				);
				server.sync;
				buffersLoaded = true;
			};
			recorder = NodeProxy.audio(server, 1).clock_(clock).quant_([beatsPerBar, 0, 0, 1]);
		}
	}

	start {
		if (buffersLoaded) {
			recorder[0] = {
				var soundIn = LeakDC.ar(SoundIn.ar(\in.kr(0), \inputLevel.kr(0.5))).tanh.scope("sampler in");
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
			"recorder: %\n".postf(recorder);
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

			recorder.play;
			isPlaying = true;
		} {
			"The sampler has not yet been initialized completely!".warn
		}
	}

	stop {
		recorder !? {
			recorder.stop;
			isPlaying = false;
		}
	}

	pause {
		recorder !? {
			recorder.pause;
		}
	}

	resume {
		recorder !? {
			recorder.resume;
		}
	}

	// schedule sampling, post current off beat if post == true
	schedule { |post=false|
		recorder !? {
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
						#[1, 4, \lin],
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
						#[1, 4, \lin],
						tempo,
						name
					)
				);
			};
			CVCenter.addActionAt(
				(nameString + "tempo").asSymbol,
				'set tempo',
				{ |cv| tempo = cv.value }
			)
		}
	}

	allocateBuffers { |numBuffers|
		// pause sampler first
		if (CVCenter.at((nameString + "on/off").asSymbol).notNil) {
			CVCenter.at((nameString + "on/off").asSymbol).input_(0)
		} {
			Error("The sampler could not be paused. Did you accidently remove the widget '" ++ nameString + "on/off'?").throw;
		};
		buffers.do{ |buf| buf.close({ |b| b.freeMsg })};
		buffers = nil;
		// allocation is asynchronous
		server.bind {
			buffers = Buffer.allocConsecutive(
				numBuffers,
				server,
				(server.sampleRate * beatsPerBar * numBars).asInteger
			);
			server.sync;
			// resume sampler
			CVCenter.at((nameString + "on/off").asSymbol).input_(1);
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