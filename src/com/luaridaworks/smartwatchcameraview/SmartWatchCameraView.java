package com.luaridaworks.smartwatchcameraview;

import java.io.ByteArrayOutputStream;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Bitmap.CompressFormat;
import android.hardware.Camera;
import android.util.Log;
import android.view.SurfaceHolder;

public class SmartWatchCameraView extends CameraPreview {

	public static final String LOG_TAG = "SmartWatchCameraView";

	private int surWidth = -1;
	private int surHeight = -1;
	private Matrix matrix90 = new Matrix();			//90度回転用
	private int[] rgb_bitmap = new int[128 * 128];	//画像切り出しよう

	//*******************************************
	// コンストラクタ
	//*******************************************
	SmartWatchCameraView(Context context) {
		super(context);

		//横向き画面固定する
		((Activity)getContext()).setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

		//90度回転用
		matrix90.postRotate(90);
	}

	//*******************************************
	// 画面サイズ変更イベント
	//*******************************************
	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		Log.d(LOG_TAG, "surfaceChanged");

		if (camera == null) {
			((Activity)context).finish();
			return;
		}

		//画面が切り替わったのでストップする
		camera.stopPreview();

		//プレビューCallbackを一応nullにする。
		camera.setPreviewCallback(null);

		//プレビュ画面のサイズ設定
		Log.d(LOG_TAG, "Width= " + width + " Height= " + height);
		surWidth = width;
		surHeight = height;
		setPictureFormat(format);
		setPreviewSize(surWidth, surHeight);

		//コールバックを再定義する
		camera.setPreviewCallback(_previewCallback);

		//プレビュスタート
		camera.startPreview();
	}

	//*******************************************
	// フレームデータを取得するためのプレビューコールバック
	//*******************************************
	private final Camera.PreviewCallback _previewCallback =
			new Camera.PreviewCallback() {

		//*******************************************
		// dataは YUV420は 1画素が12ビット
		//*******************************************
		public void onPreviewFrame(byte[] data, Camera backcamera) {
			//Log.d(LOG_TAG, "_previewCallback data.length=" + data.length + " data=" + data);

			if (camera == null) { 	return; }	//カメラが死んだ時用のブロック

			//プレビュを一時止める
			camera.stopPreview();

			//一応コールバックをnullにする
			camera.setPreviewCallback(null);

			//YUV420からRGBに変換しつつ画像中心の128×128エリアを切り出す
			decodeYUV420SP2(rgb_bitmap, data, surWidth, surHeight, surWidth/2-64, surHeight/2-64, 128, 128);

			//Bitmapを生成して、画像データを作る
			Bitmap motoBitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.RGB_565);
			Canvas motoCanvas = new Canvas(motoBitmap);
			motoCanvas.drawBitmap(rgb_bitmap, 0, 128, 0, 0, 128, 128, false, null);

			//画像を90゜回転する
			motoBitmap = Bitmap.createBitmap(motoBitmap, 0, 0, 128, 128, matrix90, true);

			//intentを出す
	        ByteArrayOutputStream baos = new ByteArrayOutputStream();
	        motoBitmap.compress(CompressFormat.PNG, 100, baos);
	        byte[] bytebmp = baos.toByteArray();

	        Intent intent = new Intent("com.luaridaworks.extras.BITMAP_SEND");
	        intent.putExtra("BITMAP", bytebmp);
	        getContext().sendBroadcast(intent);


			if (camera == null) {
				return;
			}
			else{
				//コールバックを再セットする
				camera.setPreviewCallback(_previewCallback);

				//プレビューを開始する
				camera.startPreview();
			}
		}

		//*******************************************
		// YUV420をRGBに変換する
		// データフォーマットは、最初に画面サイズ(Width*Height)分のY値が並び、
		// 以降は、横方向、縦方向共に、V,Uの順番に2画素分を示して並ぶ
		//
		// 4×3ドットがあったとすると、YUV420のデータは
		//  0 1 2 3
		// 0○○○○　Y00 Y01 Y02 Y03 Y10 Y11 Y12 Y13 Y20 Y21 Y22 Y23 V00 U00 V02 U02 V20 U20 V22 U22 となる。
		// 1○○○○　V00はY00,Y01,Y10,Y11の4ピクセルの赤色差を表し、U00はY00,Y01,Y10,Y11の4ピクセルの青色差を表す
		// 2○○○○
		//
		// width×heightの画像から (offsetX,offsetY)座標を左上座標としたgetWidth,GetHeightサイズのrgb画像を取得する
		//*******************************************
		public void decodeYUV420SP2(int[] int_rgb, byte[] yuv420sp, int width, int height, int offsetX, int offsetY, int getWidth, int getHeight) {

			//全体ピクセル数を求める
			final int frameSize = width * height;

			int uvp, y;
			int y1164, r, g, b;
			int i, yp;
			int u = 0;
			int v = 0;
			int uvs = 0;

			if(offsetY+getHeight>height){
				getHeight = height - offsetY;
			}

			if(offsetX+getWidth>width){
				getWidth = width - offsetX;
			}

			int qp = 0;	//rgb配列番号

			for (int j = offsetY; j < offsetY + getHeight; j++) {
				//1ライン毎の処理
				uvp = frameSize + (j >> 1) * width;

				//offsetXが奇数の場合は、1つ前のU,Vの値を取得する
				if((offsetX & 1)!=0){
					uvs = uvp + offsetX-1;
					// VとUのデータは、2つに1つしか存在しない。よって、iが偶数のときに読み出す
					v = (0xff & yuv420sp[uvs]) - 128;		//無彩色(色差0)が128なので、128を引く
					u = (0xff & yuv420sp[uvs + 1]) - 128;		//無彩色(色差0)が128なので、128を引く
				}

				for (i = offsetX; i < offsetX + getWidth; i++) {

					yp = j*width + i;

					//左からピクセル単位の処理
					y = (0xff & ((int) yuv420sp[yp])) - 16;		//Yの下限が16だから、16を引きます
					if (y < 0){
						y = 0;
					}

					if ((i & 1) == 0) {
						uvs = uvp + i;
						// VとUのデータは、2つに1つしか存在しない。よって、iが偶数のときに読み出す
						v = (0xff & yuv420sp[uvs]) - 128;		//無彩色(色差0)が128なので、128を引く
						u = (0xff & yuv420sp[uvs + 1]) - 128;		//無彩色(色差0)が128なので、128を引く
					}

					//変換の計算式によりR,G,Bを求める(Cb=U, Cr=V)
					// R = 1.164(Y-16)                 + 1.596(Cr-128)
					// G = 1.164(Y-16) - 0.391(Cb-128) - 0.813(Cr-128)
					// B = 1.164(Y-16) + 2.018(Cb-128)
					y1164 = 1164 * y;
					r = (y1164 + 1596 * v);
					g = (y1164 - 391 * u - 813 * v);
					b = (y1164 + 2018 * u);

					if (r < 0)
						r = 0;
					else if (r > 262143)
						r = 262143;
					if (g < 0)
						g = 0;
					else if (g > 262143)
						g = 262143;
					if (b < 0)
						b = 0;
					else if (b > 262143)
						b = 262143;

					int_rgb[qp] = 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
					qp++;
				}
			}
		}
	};
}