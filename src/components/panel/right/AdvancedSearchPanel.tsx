import { useState, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { Search, Star, X, Loader2, Calendar, Camera, Tag, FileText, SlidersHorizontal } from 'lucide-react';
import clsx from 'clsx';
import CollapsibleSection from '../../ui/CollapsibleSection';
import Button from '../../ui/Button';
import Text from '../../ui/Text';
import { TextColors, TextVariants, TextWeights } from '../../../types/typography';
import { useAdvancedSearch, SearchResultItem } from '../../../hooks/useAdvancedSearch';
import { SmartAlbumCriteria } from '../../../hooks/useSmartAlbums';
import { useLibraryStore } from '../../../store/useLibraryStore';
import { useEditorStore } from '../../../store/useEditorStore';
import { useUIStore } from '../../../store/useUIStore';

function StarRating({ value, onChange, max = 5 }: { value: number; onChange: (v: number) => void; max?: number }) {
    return (
        <div className="flex items-center gap-0.5">
            {Array.from({ length: max }, (_, i) => (
                <button
                    key={i}
                    className={clsx(
                        'p-0.5 transition-colors',
                        i < value ? 'text-yellow-400' : 'text-text-secondary/30',
                    )}
                    onClick={() => onChange(i + 1 === value ? 0 : i + 1)}
                >
                    <Star size={14} fill={i < value ? 'currentColor' : 'none'} />
                </button>
            ))}
        </div>
    );
}

export default function AdvancedSearchPanel() {
    const { t } = useTranslation();
    const { isSearching, results, totalCount, search, clearResults } = useAdvancedSearch();
    const rootPaths = useLibraryStore((s) => s.rootPaths);

    // Search criteria state
    const [textSearch, setTextSearch] = useState('');
    const [aiTags, setAiTags] = useState('');
    const [minRating, setMinRating] = useState(0);
    const [maxRating, setMaxRating] = useState(5);
    const [dateFrom, setDateFrom] = useState('');
    const [dateTo, setDateTo] = useState('');
    const [cameraModel, setCameraModel] = useState('');
    const [rawOnly, setRawOnly] = useState(false);
    const [editedOnly, setEditedOnly] = useState(false);

    // Sort and options state
    const [sortBy, setSortBy] = useState('name');
    const [sortDirection, setSortDirection] = useState('desc');

    // Collapsible section state
    const [criteriaOpen, setCriteriaOpen] = useState(true);
    const [resultsOpen, setResultsOpen] = useState(true);

    const buildCriteria = useCallback((): SmartAlbumCriteria => {
        const criteria: SmartAlbumCriteria = {};

        if (textSearch.trim()) {
            criteria.description_search = textSearch.trim();
        }
        if (aiTags.trim()) {
            criteria.ai_tags = aiTags.split(',').map(s => s.trim()).filter(Boolean);
        }
        if (minRating > 0) {
            criteria.min_rating = minRating;
        }
        if (maxRating < 5) {
            criteria.max_rating = maxRating;
        }
        if (dateFrom) {
            criteria.date_from = dateFrom;
        }
        if (dateTo) {
            criteria.date_to = dateTo;
        }
        if (cameraModel.trim()) {
            criteria.camera_models = cameraModel.split(',').map(s => s.trim()).filter(Boolean);
        }
        if (rawOnly) {
            criteria.raw_only = true;
        }
        if (editedOnly) {
            criteria.edited_only = true;
        }

        return criteria;
    }, [textSearch, aiTags, minRating, maxRating, dateFrom, dateTo, cameraModel, rawOnly, editedOnly]);

    const handleSearch = useCallback(async () => {
        const criteria = buildCriteria();
        const paths = rootPaths?.length ? rootPaths : [];
        await search(criteria, paths, { sortBy, sortDirection });
    }, [buildCriteria, rootPaths, search, sortBy, sortDirection]);

    const handleClear = useCallback(() => {
        setTextSearch('');
        setAiTags('');
        setMinRating(0);
        setMaxRating(5);
        setDateFrom('');
        setDateTo('');
        setCameraModel('');
        setRawOnly(false);
        setEditedOnly(false);
        setSortBy('name');
        setSortDirection('desc');
        clearResults();
    }, [clearResults]);

    const handleResultClick = useCallback((item: SearchResultItem) => {
        const { setLibrary } = useLibraryStore.getState();
        const { setUI } = useUIStore.getState();
        const { selectedImage, setEditor } = useEditorStore.getState();

        if (selectedImage?.path === item.path) return;

        setLibrary({ multiSelectedPaths: [item.path] });
        setEditor({
            showOriginal: false,
            activeMaskId: null,
            activeMaskContainerId: null,
            activeAiPatchContainerId: null,
            activeAiSubMaskId: null,
        });
        setUI({ isLibraryExportPanelVisible: false });
    }, []);

    const inputClassName = 'w-full bg-bg-primary border border-border-primary rounded-md px-3 py-1.5 text-sm text-text-primary placeholder-text-secondary/50 focus:outline-none focus:border-accent transition-colors';

    return (
        <div className="flex flex-col gap-3 p-4 h-full overflow-y-auto">
            <div className="flex items-center gap-2">
                <Search size={18} className="text-accent shrink-0" />
                <Text variant={TextVariants.title} weight={TextWeights.semibold}>
                    {t('editor.advancedSearch.title')}
                </Text>
            </div>

            <CollapsibleSection
                isOpen={criteriaOpen}
                onToggle={() => setCriteriaOpen(!criteriaOpen)}
                isContentVisible={true}
                canToggleVisibility={false}
                title={t('editor.advancedSearch.title')}
            >
                <div className="flex flex-col gap-3">
                    {/* Text Search */}
                    <div className="flex flex-col gap-1">
                        <label className="flex items-center gap-1.5 text-xs font-medium text-text-secondary">
                            <FileText size={12} />
                            {t('editor.advancedSearch.textSearch')}
                        </label>
                        <input
                            type="text"
                            className={inputClassName}
                            value={textSearch}
                            onChange={(e) => setTextSearch(e.target.value)}
                            placeholder={t('editor.advancedSearch.textSearch')}
                        />
                    </div>

                    {/* AI Tags */}
                    <div className="flex flex-col gap-1">
                        <label className="flex items-center gap-1.5 text-xs font-medium text-text-secondary">
                            <Tag size={12} />
                            {t('editor.advancedSearch.aiTags')}
                        </label>
                        <input
                            type="text"
                            className={inputClassName}
                            value={aiTags}
                            onChange={(e) => setAiTags(e.target.value)}
                            placeholder="tag1, tag2, ..."
                        />
                    </div>

                    {/* Rating */}
                    <div className="flex flex-col gap-1">
                        <label className="flex items-center gap-1.5 text-xs font-medium text-text-secondary">
                            <Star size={12} />
                            {t('editor.advancedSearch.ratingRange')}
                        </label>
                        <div className="flex items-center gap-3">
                            <div className="flex items-center gap-1">
                                <Text variant={TextVariants.micro} color={TextColors.secondary}>Min</Text>
                                <StarRating value={minRating} onChange={setMinRating} />
                            </div>
                            <div className="flex items-center gap-1">
                                <Text variant={TextVariants.micro} color={TextColors.secondary}>Max</Text>
                                <StarRating value={maxRating} onChange={setMaxRating} />
                            </div>
                        </div>
                    </div>

                    {/* Date Range */}
                    <div className="flex flex-col gap-1">
                        <label className="flex items-center gap-1.5 text-xs font-medium text-text-secondary">
                            <Calendar size={12} />
                            {t('editor.advancedSearch.dateRange')}
                        </label>
                        <div className="flex items-center gap-2">
                            <input
                                type="date"
                                className={clsx(inputClassName, 'flex-1')}
                                value={dateFrom}
                                onChange={(e) => setDateFrom(e.target.value)}
                            />
                            <Text variant={TextVariants.micro} color={TextColors.secondary}>—</Text>
                            <input
                                type="date"
                                className={clsx(inputClassName, 'flex-1')}
                                value={dateTo}
                                onChange={(e) => setDateTo(e.target.value)}
                            />
                        </div>
                    </div>

                    {/* Camera Model */}
                    <div className="flex flex-col gap-1">
                        <label className="flex items-center gap-1.5 text-xs font-medium text-text-secondary">
                            <Camera size={12} />
                            {t('editor.advancedSearch.cameraModel')}
                        </label>
                        <input
                            type="text"
                            className={inputClassName}
                            value={cameraModel}
                            onChange={(e) => setCameraModel(e.target.value)}
                            placeholder="Canon, Sony, ..."
                        />
                    </div>

                    {/* RAW / Edited toggles */}
                    <div className="flex items-center gap-4">
                        <label className="flex items-center gap-2 cursor-pointer select-none">
                            <input
                                type="checkbox"
                                className="accent-accent"
                                checked={rawOnly}
                                onChange={(e) => setRawOnly(e.target.checked)}
                            />
                            <Text variant={TextVariants.small} color={TextColors.primary}>
                                {t('editor.advancedSearch.rawOnly')}
                            </Text>
                        </label>
                        <label className="flex items-center gap-2 cursor-pointer select-none">
                            <input
                                type="checkbox"
                                className="accent-accent"
                                checked={editedOnly}
                                onChange={(e) => setEditedOnly(e.target.checked)}
                            />
                            <Text variant={TextVariants.small} color={TextColors.primary}>
                                {t('editor.advancedSearch.editedOnly')}
                            </Text>
                        </label>
                    </div>

                    {/* Sort Options */}
                    <div className="flex flex-col gap-1">
                        <label className="flex items-center gap-1.5 text-xs font-medium text-text-secondary">
                            <SlidersHorizontal size={12} />
                            {t('editor.advancedSearch.sortBy')}
                        </label>
                        <div className="flex items-center gap-2">
                            <select
                                className={clsx(inputClassName, 'flex-1')}
                                value={sortBy}
                                onChange={(e) => setSortBy(e.target.value)}
                            >
                                <option value="name">Name</option>
                                <option value="rating">Rating</option>
                                <option value="date">Date</option>
                                <option value="camera">Camera</option>
                            </select>
                            <select
                                className={clsx(inputClassName, 'w-24')}
                                value={sortDirection}
                                onChange={(e) => setSortDirection(e.target.value)}
                            >
                                <option value="desc">Desc</option>
                                <option value="asc">Asc</option>
                            </select>
                        </div>
                    </div>

                    {/* Search / Clear buttons */}
                    <div className="flex items-center gap-2 mt-1">
                        <Button
                            onClick={handleSearch}
                            disabled={isSearching}
                            className="flex-1"
                        >
                            {isSearching ? (
                                <>
                                    <Loader2 size={14} className="animate-spin" />
                                    {t('editor.advancedSearch.searching')}
                                </>
                            ) : (
                                <>
                                    <Search size={14} />
                                    {t('editor.advancedSearch.searchButton')}
                                </>
                            )}
                        </Button>
                        <Button
                            onClick={handleClear}
                            className="bg-surface"
                        >
                            <X size={14} />
                        </Button>
                    </div>
                </div>
            </CollapsibleSection>

            {/* Results */}
            {results.length > 0 && (
                <CollapsibleSection
                    isOpen={resultsOpen}
                    onToggle={() => setResultsOpen(!resultsOpen)}
                    isContentVisible={true}
                    canToggleVisibility={false}
                    title={t('editor.advancedSearch.results', { count: totalCount })}
                >
                    <div className="flex flex-col gap-1 max-h-64 overflow-y-auto">
                        {results.map((item) => (
                            <button
                                key={item.path}
                                className="flex items-center gap-2 px-2 py-1.5 rounded-md hover:bg-card-active transition-colors text-left w-full"
                                onClick={() => handleResultClick(item)}
                            >
                                {item.rating > 0 && (
                                    <div className="flex items-center shrink-0">
                                        {Array.from({ length: item.rating }, (_, i) => (
                                            <Star key={i} size={10} className="text-yellow-400" fill="currentColor" />
                                        ))}
                                    </div>
                                )}
                                <div className="flex-1 min-w-0">
                                    <Text variant={TextVariants.small} color={TextColors.primary} className="truncate block">
                                        {item.path.split(/[\\/]/).pop()}
                                    </Text>
                                    {item.camera_model && (
                                        <Text variant={TextVariants.micro} color={TextColors.secondary} className="truncate block">
                                            {item.camera_model}
                                        </Text>
                                    )}
                                </div>
                                {item.date && (
                                    <Text variant={TextVariants.micro} color={TextColors.secondary} className="shrink-0">
                                        {item.date.split(' ')[0]}
                                    </Text>
                                )}
                            </button>
                        ))}
                    </div>
                </CollapsibleSection>
            )}

            {/* No results message */}
            {!isSearching && totalCount === 0 && results.length === 0 && (
                <div className="flex items-center justify-center py-8">
                    <Text variant={TextVariants.small} color={TextColors.secondary}>
                        {t('editor.advancedSearch.noResults')}
                    </Text>
                </div>
            )}
        </div>
    );
}
