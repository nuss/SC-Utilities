MySampler {
	classvar <all, <synthDescLib;
	var <clock, <tempo, <beatsPerBar, <numBuffers, <server, <inBus;
	var <buffers;
	var recorder, <isPlaying = false;
	var <synthDescLib;
	var cvcenterTabLabel, cvcenterWdgtBaseName;

	*new { |clock, tempo=1, beatsPerBar=4, numBuffers=5, server|
		^super.newCopyArgs(clock, tempo, beatsPerBar, numBuffers).init;
	}

	init {
		all ?? { all = [] };
		all = all.add(this);

		cvcenterTabLabel = ("sampler" + (all.indexOf(this) + 1)).asSymbol;
		cvcenterWdgtBaseName = ("sampler" ++ (all.indexOf(this) + 1));

		server ?? { server = Server.default };
		"clock: %\n".postf(clock);
		clock ?? {
			// create a new clock, compensating latency
			clock = TempoClock(tempo, 0, server.latency.neg);
		};
		"clock tempo: %\n".postf(clock.tempo);

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
			recorder[1] = \set -> Pbind(
				\dur, beatsPerBar,
				\in, CVCenter.use(
					(cvcenterWdgtBaseName + "in").asSymbol,
					ControlSpec(0, server.options.firstPrivateBus-1, \lin, 1.0),
					tab: cvcenterTabLabel
				),
				\inputLevel, CVCenter.use(
					(cvcenterWdgtBaseName + "level").asSymbol,
					\amp,
					tab: cvcenterTabLabel
				),
				\bufnum, Pseq(buffers.collect(_.bufnum), inf),
				\tempo, CVCenter.use(
					cvcenterWdgtBaseName + "tempo",
					#[0.2, 4, \lin],
					value: tempo,
					tab: cvcenterTabLabel
				)
			)
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

	// an acoustic metronome
	metronome { |out=0, numChannels=1, baseAmp=0.2|
		server.bind {
			SynthDescLib.at(\mySampler) ?? {
				// store the metronome in a separate SynthDescLib in order to avoid name clashes
				synthDescLib = SynthDescLib(\mySampler, [server]);
			};
			SynthDef(\metronome, {
				var env = EnvGen.ar(Env.perc(0.001, 0.1), doneAction: 2);
				Out.ar(\out.kr(out), SinOsc.ar(\freq.kr(330) ! numChannels, mul: env * \baseAmp.kr * \amp.kr(1)));
			}).add(\mySampler);
			server.sync;
			Pdef(("metronome" ++ (all.indexOf(this) + 1)).asSymbol,
				Pbind(
					\synthLib, SynthDescLib.all[\mySampler],
					\instrument, \metronome,
					\freq, Pseq([440] ++ (330 ! (beatsPerBar - 1)), inf),
					\dur, 1,
					\baseAmp, Pseq([1] ++ (0.7 ! (beatsPerBar - 1)), inf),
					\amp, CVCenter.use(cvcenterWdgtBaseName + "metroAmp", value: 1, tab: cvcenterTabLabel)
				)
			).play(clock);
		};
	}
}