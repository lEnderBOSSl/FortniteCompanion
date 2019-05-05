package com.tb24.fn.activity;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tb24.fn.R;
import com.tb24.fn.model.CommonCoreProfileAttributes;
import com.tb24.fn.model.EpicError;
import com.tb24.fn.model.FortCatalogResponse;
import com.tb24.fn.model.FortItemStack;
import com.tb24.fn.model.FortMcpResponse;
import com.tb24.fn.model.PurchaseCatalogEntry;
import com.tb24.fn.util.JsonUtils;
import com.tb24.fn.util.LoadingViewController;
import com.tb24.fn.util.Utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import retrofit2.Call;
import retrofit2.Response;

public class ItemShopActivity extends BaseActivity {
	public static final String CONFIRM_PHRASE = "CONFIRM";
	private boolean fakePurchases = false;
	private SoundPool soundPool;
	private RecyclerView list;
	private ItemShopAdapter adapter;
	private LoadingViewController lc;
	private GridLayoutManager layout;
	private ViewGroup vBucksView;
	private CommonCoreProfileAttributes attributes;
	private int vbucks;
	private int[] sounds;

	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.common_loadable_recycler_view);
		setupActionBar();
		fakePurchases = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("fake_purchases", false);
		attributes = getThisApplication().gson.fromJson(getThisApplication().dataCommonCore.profileChanges.get(0).profile.stats.attributes, CommonCoreProfileAttributes.class);
		soundPool = new SoundPool.Builder().setMaxStreams(4).setAudioAttributes(new AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
		int purchasedSound1 = soundPool.load(this, R.raw.store_purchaseitem_athena_01, 1);
		int purchasedSound2 = soundPool.load(this, R.raw.store_purchaseitem_athena_02, 1);
		sounds = new int[]{purchasedSound1, purchasedSound2};
		list = findViewById(R.id.main_recycler_view);
		int p = (int) Utils.dp(getResources(), 4);
		list.setPadding(p, p, p, p);
		list.setClipToPadding(false);
		list.post(new Runnable() {
			@Override
			public void run() {
				layout = new GridLayoutManager(ItemShopActivity.this, (int) (list.getWidth() / Utils.dp(getResources(), 200)));
				list.setLayoutManager(layout);
			}
		});
		lc = new LoadingViewController(this, list, "");
		load();
	}

	private void load() {
		final Call<FortCatalogResponse> call = getThisApplication().fortnitePublicService.storefrontCatalog();
		lc.loading();
		new Thread(new Runnable() {
			@Override
			public void run() {
				String errorText = "";
				try {
					final Response<FortCatalogResponse> response = call.execute();

					if (response.isSuccessful()) {
						runOnUiThread(new Runnable() {
							@Override
							public void run() {
								display(response.body());
							}
						});
					} else {
						errorText = EpicError.parse(response).getDisplayText();
					}
				} catch (IOException e) {
					errorText = e.toString();
				}

				final String finalText = errorText;
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						if (!finalText.isEmpty()) {
							lc.error(finalText);
						}
					}
				});
			}
		}).start();
	}

	public void display(FortCatalogResponse data) {
		Toast.makeText(this, "Expiration: " + Utils.formatDateSimple(data.expiration), Toast.LENGTH_LONG).show();
		List<FortCatalogResponse.CatalogEntry> entries = new ArrayList<>();

		for (FortCatalogResponse.Storefront storefront : data.storefronts) {
			if (storefront.name.equals("BRWeeklyStorefront") || storefront.name.equals("BRDailyStorefront")) {
				entries.addAll(Arrays.asList(storefront.catalogEntries));
			}
		}

		list.setAdapter(adapter = new ItemShopAdapter(this, entries));
		lc.content();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add("V-Bucks").setActionView(vBucksView = (ViewGroup) LayoutInflater.from(this).inflate(R.layout.vbucks, null)).setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS);
		updateFromProfile();
		menu.add(0, 1, 0, "Support a Creator");
		return super.onCreateOptionsMenu(menu);
	}

	private void updateFromProfile() {
		vbucks = MainActivity.countAndSetVbucks(this, vBucksView);

		if (adapter != null) {
			adapter.notifyDataSetChanged();
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == 1) {
			View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_text, null);
			final EditText editText = view.findViewById(R.id.dialog_edit_text_field);
			DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					if (which == DialogInterface.BUTTON_POSITIVE) {
						String s = editText.getText().toString();

						if (!s.equals(attributes.mtx_affiliate)) {
//							executeSetAffiliate(s);
						}
					} else if (which == DialogInterface.BUTTON_NEUTRAL) {
						startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.fortnite.com/creator-list")));
					}
				}
			};
			final AlertDialog ad = new AlertDialog.Builder(this)
					.setTitle("Support a Creator")
					.setMessage("Declare your support for a Creator! Your in-game purchases will help support this Creator.")
					.setView(view)
					.setPositiveButton("Accept", listener)
					.setNegativeButton("Close", listener)
					.setNeutralButton("View Approved Creators", listener)
					.show();
			editText.setText(attributes.mtx_affiliate);
			editText.requestFocus();
			final Button button = ad.getButton(DialogInterface.BUTTON_POSITIVE);
			//TODO figure out SetAffiliateName
			editText.setEnabled(false);
			button.setEnabled(false);
			editText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
				@Override
				public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
					if (i == EditorInfo.IME_ACTION_DONE) {
						if (button.isEnabled()) {
							button.callOnClick();
						}

						return true;
					}

					return false;
				}
			});
		}

		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		soundPool.release();
	}

	//	private void executeSetAffiliate(String s) {
//		new Thread() {
//			@Override
//			public void run() {
//				Call<FortMcpResponse> call = getThisApplication().fortnitePublicService.mcp("SetAffiliateName", PreferenceManager.getDefaultSharedPreferences(ItemShopActivity.this).getString("epic_account_id", ""), "common_core", -1, true, null);
//			}
//		}.start();
//	}

	private static class ItemShopAdapter extends RecyclerView.Adapter<ItemShopAdapter.ItemShopViewHolder> {
		private final ItemShopActivity activity;
		private final List<FortCatalogResponse.CatalogEntry> data;
		private final Map<String, Bitmap> bitmapCache = new HashMap<>();
//		private final Map<String, JsonElement> extraData2 = new HashMap<>();

		public ItemShopAdapter(ItemShopActivity activity, List<FortCatalogResponse.CatalogEntry> data) {
			this.activity = activity;
			this.data = data;
		}

		@NonNull
		@Override
		public ItemShopViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			ViewGroup itemView = (ViewGroup) LayoutInflater.from(parent.getContext()).inflate(R.layout.item_shop_entry, parent, false);
			ViewGroup.LayoutParams layoutParams = itemView.getLayoutParams();
//			layoutParams.width = (int) Utils.dp(activity.getResources(), (int) ((float) activity.list.getWidth() / activity.layout.getSpanCount()));
			return new ItemShopViewHolder(itemView);
		}

		@Override
		public void onBindViewHolder(@NonNull final ItemShopViewHolder holder, final int position) {
			final FortCatalogResponse.CatalogEntry item = data.get(position);
			holder.backgroundable.setBackgroundResource(R.drawable.bg_common);
			holder.shortDescription.setText(null);
			Bitmap bitmap = null;
			JsonElement jsonFirst = null;
			String[] fromDevName = item.devName.substring("[VIRTUAL]".length(), item.devName.lastIndexOf(" for ")).replaceAll("1 x ", "").split(", ");
			List<String> compiledNames = new ArrayList<>();
			final List<JsonElement> jsons = new ArrayList<>();

			for (int i = 0; i < item.itemGrants.length; i++) {
				FortItemStack itemStack = item.itemGrants[i];
				JsonElement json = activity.getThisApplication().itemRegistry.get(itemStack.templateId);
				jsons.add(json);

				if (json == null) {
					compiledNames.add(fromDevName[i]);
					continue;
				}

				JsonObject jsonObject = json.getAsJsonArray().get(0).getAsJsonObject();

				if (i == 0) {
					jsonFirst = json;

					if (bitmapCache.containsKey(itemStack.templateId)) {
						bitmap = bitmapCache.get(itemStack.templateId);
					} else {
						bitmapCache.put(itemStack.templateId, bitmap = getBitmapImageFromItemStackData(activity, itemStack, jsonObject));
					}

					try {
						holder.shortDescription.setText(shortDescription(itemStack, jsonObject));
						holder.backgroundable.setBackgroundResource(rarityBackground(jsonObject));
					} catch (NullPointerException e) {
						Log.w("ItemShopActivity", "Failed setting short description or rarity background for " + itemStack.templateId, e);
					}
				}

				compiledNames.add(jsonObject.get("DisplayName").getAsString());
			}

			holder.itemName.setText(compiledNames.get(0));
			holder.displayImage.setImageBitmap(bitmap);
			boolean owned = false;

			if (activity.getThisApplication().dataAthena != null) {
				for (FortCatalogResponse.Requirement requirement : item.requirements) {
					if (requirement.requirementType.equals("DenyOnItemOwnership")) {
						for (Map.Entry<String, FortItemStack> inventoryItem : activity.getThisApplication().dataAthena.profileChanges.get(0).profile.items.entrySet()) {
							if (inventoryItem.getValue().templateId.equals(requirement.requiredId) && inventoryItem.getValue().quantity >= requirement.minQuantity) {
								owned = true;
								break;
							}
						}

						break;
					}
				}
			}

			holder.priceGroup.setVisibility(owned ? View.GONE : View.VISIBLE);
			holder.owned.setVisibility(owned ? View.VISIBLE : View.GONE);
			final FortCatalogResponse.Price price = item.prices[0];
			String banner = null;

			if (price.saleType != null) {
				banner = "On sale!";
				holder.itemSale.setVisibility(View.VISIBLE);
				holder.itemSale.setText(String.format("%,d", price.regularPrice));
			} else {
				holder.itemSale.setVisibility(View.GONE);
			}

			if (item.meta.has("BannerOverride")) {
				banner = item.meta.get("BannerOverride").getAsString();
			}

			if (banner != null) {
				if (banner.equals("CollectTheSet")) {
					banner = "Collect the set!";
				} else if (banner.equals("New")) {
					banner = "New!";
				}
			}

			holder.banner.setText(banner);
			holder.banner.setVisibility(banner == null ? View.INVISIBLE : View.VISIBLE);
			holder.itemPrice.setText(String.format("%,d", price.basePrice));

//			TODO bg blur for the blacked thing
//			if (bitmap != null) {
//				Blurry.with(activity).radius(10).capture(holder.itemView).into(holder.blur);
//			} else {
//				holder.blur.setImageDrawable(null);
//			}

			final boolean finalOwned = owned;
			final JsonElement finalJsonFirst = jsonFirst;
			holder.itemView.setOnClickListener(new View.OnClickListener() {
				private ViewGroup view;
				private ViewGroup owned;
				private Button btnPurchase, btnGift;
				private View.OnClickListener buttonsClickListener;
				private boolean purchasePending, purchaseSuccess;
				private int previewingIndex = 0;

				@Override
				public void onClick(View v) {
					view = (ViewGroup) LayoutInflater.from(activity).inflate(R.layout.item_shop_panel_detail, null);
					populateView();
					AlertDialog alertDialog = new AlertDialog.Builder(activity).setView(view).create();
					alertDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
					alertDialog.getWindow().setWindowAnimations(R.style.ItemShopAnim);
					alertDialog.show();
				}

				private void populateView() {
					TextView priceTv = view.findViewById(R.id.item_price);
					TextView itemSale = view.findViewById(R.id.item_sale_from);
					owned = view.findViewById(R.id.item_owned);
					btnPurchase = view.findViewById(R.id.btn_item_shop_purchase);
					btnGift = view.findViewById(R.id.btn_item_shop_gift);
					ViewGroup sacRoot = view.findViewById(R.id.sac_root);
					TextView sacName = view.findViewById(R.id.sac_name);
					ViewGroup group = view.findViewById(R.id.item_shop_all_item_grants);
					updateItemInfo();

					if (item.itemGrants.length > 1) {
						for (int i = 0; i < item.itemGrants.length; i++) {
							FortItemStack itemStackLoop = item.itemGrants[i];
							View slotView = LayoutInflater.from(activity).inflate(R.layout.slot_view, null);
							populateSlotView(itemStackLoop, slotView, jsons.get(i));
							final int finalI = i;
							slotView.setOnClickListener(new View.OnClickListener() {
								@Override
								public void onClick(View v) {
									previewingIndex = finalI;
									updateItemInfo();
								}
							});
							LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) Utils.dp(activity.getResources(), 66), (int) Utils.dp(activity.getResources(), 110));
							lp.setMarginEnd((int) Utils.dp(activity.getResources(), 8));
							group.addView(slotView, lp);
						}
					} else {
						// TODO big item slot icon
					}

					priceTv.setText(String.format("%,d", price.basePrice));

					if (price.saleType != null) {
						itemSale.setVisibility(View.VISIBLE);
						itemSale.setText(String.format("%,d", price.regularPrice));
					} else {
						itemSale.setVisibility(View.GONE);
					}

					buttonsClickListener = new View.OnClickListener() {
						@Override
						public void onClick(View v) {
							if (v == btnPurchase) {
								if (!activity.fakePurchases) {
									Utils.createEditTextDialog(activity, "Warning! You will commence a real transaction. Type \"" + CONFIRM_PHRASE + "\" to proceed.", activity.getString(android.R.string.ok), new Utils.EditTextDialogCallback() {
										@Override
										public void onResult(String s) {
											if (s.equals(CONFIRM_PHRASE)) {
												doPurchase(item);
											}
										}
									});
								} else {
									doPurchase(item);
								}
							} else if (v == btnGift) {
								Toast.makeText(activity, "Will make it later!", Toast.LENGTH_SHORT).show();
							}
						}
					};
					btnPurchase.setOnClickListener(buttonsClickListener);
					btnGift.setOnClickListener(buttonsClickListener);
					updateButtons();
					view.findViewById(R.id.no_refund).setVisibility(item.refundable ? View.GONE : View.VISIBLE);

					if (!activity.attributes.mtx_affiliate.isEmpty()) {
						sacRoot.setVisibility(View.VISIBLE);
						sacName.setText(activity.attributes.mtx_affiliate);
					} else {
						sacRoot.setVisibility(View.GONE);
					}
				}

				private void updateItemInfo() {
					FortItemStack itemStack = item.itemGrants[previewingIndex];
					JsonElement json = jsons.get(previewingIndex);

					if (json != null) {
						BaseActivity.decorateItemDetailBox(view, itemStack, json);
					}
				}

				private void updateButtons() {
					boolean finallyOwned = purchaseSuccess || finalOwned;
					owned.setVisibility(finallyOwned ? View.VISIBLE : View.GONE);
					btnPurchase.setText(purchasePending ? "Purchase pending" : item.itemGrants.length == 1 ? "Purchase" : "Purchase items");
					btnPurchase.setEnabled(!purchasePending);
					btnPurchase.setVisibility(finallyOwned ? View.GONE : View.VISIBLE);

					if (!finallyOwned && activity.vbucks < price.basePrice) {
						btnPurchase.setEnabled(false);
						btnPurchase.setText("Not enough V-Bucks");
					}

					btnGift.setVisibility(activity.attributes.allowed_to_send_gifts && item.giftInfo != null && item.giftInfo.bIsEnabled ? View.VISIBLE : View.GONE);
				}

				private void doPurchase(final FortCatalogResponse.CatalogEntry item) {
					PurchaseCatalogEntry payload = new PurchaseCatalogEntry();
					payload.currency = item.prices[0].currencyType;
					payload.currencySubType = item.prices[0].currencySubType;
					payload.expectedPrice = item.prices[0].basePrice;
					payload.offerId = item.offerId;
					final Call<FortMcpResponse> call = activity.getThisApplication().fortnitePublicService.mcp("PurchaseCatalogEntry", PreferenceManager.getDefaultSharedPreferences(activity).getString("epic_account_id", ""), "common_core", -1, true, payload);
					purchasePending = true;
					updateButtons();
					new Thread("Purchase Worker") {
						@Override
						public void run() {
							try {
								if (!activity.fakePurchases) {
									//TODO whats the response
									Response<FortMcpResponse> mcpResponse = call.execute();

									if (mcpResponse.isSuccessful()) {
										activity.getThisApplication().dataCommonCore = mcpResponse.body();
										// TODO profile update listener
										// hooray
										activity.runOnUiThread(new Runnable() {
											@Override
											public void run() {
												purchaseSuccess();
											}
										});
									} else {
										Utils.dialogError(activity, EpicError.parse(mcpResponse).getDisplayText());
									}
								} else {
									// fake it
									Thread.sleep(2000);
									purchaseSuccess();
								}
							} catch (Exception e) {
								Utils.networkErrorDialog(activity, e);
							} finally {
								activity.runOnUiThread(new Runnable() {
									@Override
									public void run() {
										purchasePending = false;
										updateButtons();
									}
								});
							}
						}
					}.start();
				}

				private void purchaseSuccess() {
					activity.runOnUiThread(new Runnable() {
						@Override
						public void run() {
							purchaseSuccess = true;
							activity.setResult(RESULT_OK);
							FortItemStack firstItemGrant = item.itemGrants[0];
							String firstItemName = firstItemGrant.templateId;

							if (finalJsonFirst != null) {
								firstItemName = JsonUtils.getStringOr("DisplayName", finalJsonFirst.getAsJsonArray().get(0).getAsJsonObject(), firstItemGrant.templateId);
							}

							View purchasedDialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_purchased, null);
							View purchasedText = purchasedDialogView.findViewById(R.id.item_shop_purchased_text);
							View purchasedCheck = purchasedDialogView.findViewById(R.id.item_shop_purchased_check);
							TextView purchasedItemTitle = purchasedDialogView.findViewById(R.id.item_shop_purchased_item_title);
							View slotView = purchasedDialogView.findViewById(R.id.to_set_background);
							purchasedItemTitle.setText(firstItemName);
							populateSlotView(firstItemGrant, purchasedDialogView, jsons.get(0));
							final Dialog dialog = new Dialog(activity);
							dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
							dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
							dialog.getWindow().getDecorView().setSystemUiVisibility(0);
							dialog.getWindow().setStatusBarColor(0);
							dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
							dialog.setCanceledOnTouchOutside(false);
							dialog.setContentView(purchasedDialogView);
							dialog.show();
							dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
							int duration = 250;
							Interpolator bounceInterpolator = AnimationUtils.loadInterpolator(activity, R.anim.elastic_50_menu_popup);
							slotView.setScaleX(2.75F);
							slotView.setScaleY(2.75F);
							slotView.setAlpha(0.0F);
							slotView.animate().scaleX(1.0F).scaleY(1.0F).alpha(1.0F).setDuration(500).start();
							purchasedItemTitle.setTranslationX(400.0F);
							purchasedItemTitle.setAlpha(0.0F);
							purchasedItemTitle.animate().translationX(0.0F).alpha(1.0F).setInterpolator(bounceInterpolator).setDuration(duration).setStartDelay(duration).start();
							purchasedText.setTranslationY(100.0F);
							purchasedText.setScaleX(0.0F);
							purchasedText.animate().translationY(0.0F).scaleX(1.0F).setInterpolator(bounceInterpolator).setDuration(duration).setStartDelay(duration + duration).start();
							purchasedCheck.setRotation(540.0F);
							purchasedCheck.setScaleX(2.0F);
							purchasedCheck.setScaleY(2.0F);
							purchasedCheck.setAlpha(0.0F);
							purchasedCheck.animate().rotation(0.0F).scaleX(1.0F).scaleY(1.0F).alpha(1.0F).setInterpolator(bounceInterpolator).setDuration(375).setStartDelay(duration + duration + duration).start();
							activity.soundPool.play(activity.sounds[new Random().nextInt(activity.sounds.length)], 1.0F, 1.0F, 0, 0, 1.0F);
							purchasedDialogView.postDelayed(new Runnable() {
								@Override
								public void run() {
									dialog.dismiss();
								}
							}, 4000);
							activity.updateFromProfile();
						}
					});
				}
			});
		}

		private void populateSlotView(FortItemStack itemStack, View slotView, JsonElement json) {
			View rarityBackground = slotView.findViewById(R.id.to_set_background);
			TextView quantity = slotView.findViewById(R.id.item_slot_quantity);
			rarityBackground.setBackgroundResource(R.drawable.bg_common);
			Bitmap bitmap = null;

			if (json != null) {
				JsonObject jsonObject = json.getAsJsonArray().get(0).getAsJsonObject();
				bitmap = getBitmapImageFromItemStackData(activity, itemStack, jsonObject);
				rarityBackground.setBackgroundResource(rarityBackground(jsonObject));
			}

			((ImageView) slotView.findViewById(R.id.item_img)).setImageBitmap(bitmap);
			((TextView) slotView.findViewById(R.id.item_slot_dbg_text)).setText(bitmap == null ? itemStack.templateId : null);
			quantity.setVisibility(itemStack.quantity > 1 ? View.VISIBLE : View.GONE);
			quantity.setText(String.valueOf(itemStack.quantity));
		}


		@Override
		public int getItemCount() {
			return data.size();
		}

		static class ItemShopViewHolder extends RecyclerView.ViewHolder {
			ImageView displayImage;//, blur;
			TextView itemName, itemPrice, itemSale, shortDescription, banner;
			ViewGroup backgroundable, owned, priceGroup;

			ItemShopViewHolder(View itemView) {
				super(itemView);
				displayImage = itemView.findViewById(R.id.item_img);
//				blur = itemView.findViewById(R.id.bg_blur);
				itemName = itemView.findViewById(R.id.item_text1);
				itemPrice = itemView.findViewById(R.id.item_text2);
				itemSale = itemView.findViewById(R.id.item_sale_from);
				shortDescription = itemView.findViewById(R.id.item_text3);
				banner = itemView.findViewById(R.id.news_entry_adspace);
				priceGroup = itemView.findViewById(R.id.item_price_group);
				owned = itemView.findViewById(R.id.item_owned);
				backgroundable = itemView.findViewById(R.id.to_set_background);
			}
		}
	}
}